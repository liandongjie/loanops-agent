package com.loanops.policy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanops.conversation.ConversationHistoryMessage;
import com.loanops.policy.PolicyTypes.DocumentInput;
import com.loanops.policy.PolicyTypes.RetrievalHit;
import com.loanops.policy.PolicyTypes.RetrievalResult;
import com.loanops.policy.PolicyTypes.VersionInput;
import com.loanops.policy.PolicyTypes.VersionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "POLICY_RAG_EVAL", matches = "true")
@SpringBootTest(properties = "loanops.policy.qdrant.collection=loanops_policy_rag_eval")
@ActiveProfiles({"mysql", "policy"})
class PolicyRagEvaluationIntegrationTest {

    private static final Path CASES_PATH = Path.of("evaluation/policy-rag-cases.json");
    private static final Path CORPUS_PATH = Path.of("evaluation/policy-rag-corpus.json");

    @Autowired private ObjectMapper objectMapper;
    @Autowired private PolicyIngestionService ingestionService;
    @Autowired private PolicyIndexRebuilder indexRebuilder;
    @Autowired private PolicyRetriever retriever;
    @Autowired private PolicyRetrievalDecisionEngine decisionEngine;
    @Autowired private PolicyQueryBuilder queryBuilder;
    @Autowired private PolicyGroundingContextFactory contextFactory;
    @Autowired private PolicyEmbeddingProvider embeddingProvider;
    @Autowired private PolicyVectorIndex vectorIndex;
    @Autowired private JdbcTemplate jdbc;
    @Value("${loanops.policy.retrieval-top-k:5}") private int topK;
    @Value("${loanops.policy.runtime.score-threshold:0.60}") private double threshold;
    @Value("${spring.ai.ollama.embedding.model:bge-m3}") private String embeddingModel;

    @Test
    void evaluateFrozenGoldDatasetWithRealInfrastructure() throws Exception {
        assertThat(embeddingProvider).isInstanceOf(SpringAiPolicyEmbeddingProvider.class);
        assertThat(vectorIndex).isInstanceOf(QdrantPolicyVectorIndex.class);
        byte[] corpusBytes = Files.readAllBytes(CORPUS_PATH);
        EvalCorpus corpus = objectMapper.readValue(corpusBytes, EvalCorpus.class);
        EvalDataset dataset = objectMapper.readValue(CASES_PATH.toFile(), EvalDataset.class);
        assertThat(dataset.cases()).hasSizeBetween(27, 33);

        cleanupCorpus(corpus.sourceType());
        vectorIndex.replaceAll(List.of());
        try {
            ingest(corpus);
            int chunkCount = indexRebuilder.rebuild();
            assertThat(chunkCount).isGreaterThanOrEqualTo(30);
            int dimension = embeddingProvider.embed(List.of("维度探针")).getFirst().length;

            List<CaseResult> results = dataset.cases().stream().map(this::evaluate).toList();
            EvalReport report = report(corpus, corpusBytes, dataset, chunkCount, dimension, results);
            writeReports(report);
            System.out.printf(Locale.ROOT,
                    "POLICY_RAG_%s cases=%d chunks=%d routerAccuracy=%.4f policyRecall=%.4f "
                            + "recall1=%.4f recall3=%.4f recall5=%.4f mrr=%.4f exact=%.4f temporal=%.4f "
                            + "noMatch=%.4f falseMatches=%d%n",
                    report.label().toUpperCase(Locale.ROOT), report.caseCount(), report.corpusChunkCount(),
                    report.metrics().routerAccuracy(), report.metrics().policyRequiredRecall(),
                    report.metrics().recallAt1(), report.metrics().recallAt3(), report.metrics().recallAt5(),
                    report.metrics().mrr(), report.metrics().exactReferenceAccuracy(),
                    report.metrics().temporalVersionAccuracy(), report.metrics().noMatchAccuracy(),
                    report.metrics().falseMatchCount());
        } finally {
            vectorIndex.replaceAll(List.of());
            cleanupCorpus(corpus.sourceType());
        }
    }

    private CaseResult evaluate(EvalCase evalCase) {
        List<ConversationHistoryMessage> history = history(evalCase);
        String current = evalCase.turns().getLast().message();
        PolicyRetrievalDecision actualDecision = decisionEngine.decide(history, current);
        String actualStatus = "NOT_RUN";
        int goldRank = 0;
        boolean temporalCorrect = true;
        long durationMs = 0;
        List<HitResult> hits = List.of();

        if (evalCase.expectedRetrievalStatus() != null) {
            String query = queryBuilder.build(history, current);
            long started = System.nanoTime();
            RetrievalResult retrieval = retriever.retrieve(query, LocalDate.parse(evalCase.asOfDate()));
            durationMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            PolicyGroundingContext context = contextFactory.create(
                    PolicyRetrievalDecision.valueOf(evalCase.expectedDecision()), retrieval);
            actualStatus = context.retrievalStatus().name();
            hits = java.util.stream.IntStream.range(0, retrieval.hits().size())
                    .mapToObj(index -> HitResult.from(index + 1, retrieval.hits().get(index)))
                    .toList();
            goldRank = evalCase.expectedDocumentNumber() == null ? 0 : goldRank(evalCase, retrieval.hits());
            if ("TEMPORAL_VERSION".equals(evalCase.category())) {
                temporalCorrect = goldRank > 0 && retrieval.hits().stream()
                        .filter(hit -> hit.match().document().title().equals("贷后管理评测规程"))
                        .allMatch(hit -> evalCase.expectedDocumentNumber().equals(
                                hit.match().version().documentNumber()));
            }
        }

        Set<String> failures = new LinkedHashSet<>();
        if (!evalCase.expectedDecision().equals(actualDecision.name())) failures.add("ROUTING");
        if (evalCase.expectedRetrievalStatus() != null
                && !evalCase.expectedRetrievalStatus().equals(actualStatus)) {
            failures.add("NO_MATCH".equals(evalCase.expectedRetrievalStatus())
                    ? "NO_MATCH_THRESHOLD" : "SEMANTIC_RECALL");
        }
        if (evalCase.expectedDocumentNumber() != null && goldRank == 0) failures.add("SEMANTIC_RECALL");
        if (evalCase.expectedDocumentNumber() != null && goldRank > 1) failures.add("RANKING");
        if ("EXACT_REFERENCE".equals(evalCase.category()) && goldRank != 1) failures.add("EXACT_MATCH");
        if (!temporalCorrect) failures.add("TEMPORAL_FILTER");
        return new CaseResult(evalCase.id(), evalCase.category(), evalCase.expectedDecision(), actualDecision.name(),
                evalCase.expectedRetrievalStatus(), actualStatus, goldRank, temporalCorrect, durationMs,
                List.copyOf(failures), hits);
    }

    private EvalReport report(EvalCorpus corpus, byte[] corpusBytes, EvalDataset dataset, int chunkCount,
                              int dimension, List<CaseResult> results) {
        long routerCorrect = results.stream().filter(result -> result.expectedDecision().equals(result.actualDecision())).count();
        List<CaseResult> policyCases = results.stream()
                .filter(result -> !"NOT_REQUIRED".equals(result.expectedDecision())).toList();
        long policyRouted = policyCases.stream().filter(result -> !"NOT_REQUIRED".equals(result.actualDecision())).count();
        List<CaseResult> retrievalCases = results.stream().filter(result -> result.goldRank() >= 0)
                .filter(result -> dataset.caseById(result.id()).expectedDocumentNumber() != null).toList();
        List<CaseResult> exactCases = byCategory(results, "EXACT_REFERENCE");
        List<CaseResult> temporalCases = byCategory(results, "TEMPORAL_VERSION");
        List<CaseResult> noMatchCases = byCategory(results, "NO_MATCH");
        List<Long> latencies = results.stream().filter(result -> result.durationMs() > 0)
                .map(CaseResult::durationMs).sorted().toList();
        Metrics metrics = new Metrics(
                ratio(routerCorrect, results.size()), ratio(policyRouted, policyCases.size()),
                recall(retrievalCases, 1), recall(retrievalCases, 3), recall(retrievalCases, 5),
                retrievalCases.stream().mapToDouble(result -> result.goldRank() == 0 ? 0.0 : 1.0 / result.goldRank()).average().orElse(0),
                ratio(exactCases.stream().filter(result -> result.goldRank() == 1).count(), exactCases.size()),
                ratio(temporalCases.stream().filter(CaseResult::temporalCorrect).count(), temporalCases.size()),
                ratio(noMatchCases.stream().filter(result -> "NO_MATCH".equals(result.actualRetrievalStatus())).count(), noMatchCases.size()),
                noMatchCases.stream().filter(result -> !"NO_MATCH".equals(result.actualRetrievalStatus())).count(),
                percentile(latencies, 0.50), percentile(latencies, 0.95));
        Map<String, Map<String, Integer>> confusion = confusion(results);
        List<String> failed = results.stream().filter(result -> !result.failures().isEmpty()).map(CaseResult::id).toList();
        return new EvalReport(System.getenv().getOrDefault("POLICY_RAG_EVAL_LABEL", "e0").toLowerCase(Locale.ROOT),
                System.getenv().getOrDefault("POLICY_EVAL_GIT_HEAD", "unknown"), corpus.identifier(),
                PolicyHashing.sha256(new String(corpusBytes, java.nio.charset.StandardCharsets.UTF_8)),
                embeddingModel, dimension, topK, threshold, "deterministic exact + BGE-M3 dense",
                dataset.cases().size(), chunkCount, metrics, confusion, failed, results);
    }

    private void ingest(EvalCorpus corpus) {
        for (CorpusDocument document : corpus.documents()) {
            DocumentInput input = new DocumentInput(document.title(), document.documentType(), document.issuer(),
                    corpus.sourceType(), document.jurisdiction());
            for (CorpusVersion version : document.versions()) {
                LocalDate from = LocalDate.parse(version.effectiveFrom());
                ingestionService.ingest(input, new VersionInput(version.versionLabel(), version.documentNumber(),
                        from.minusDays(30), from.minusDays(15), from,
                        version.effectiveTo() == null ? null : LocalDate.parse(version.effectiveTo()),
                        VersionStatus.ACTIVE, version.sourceUri(), null, version.structuredText()));
            }
        }
    }

    private void cleanupCorpus(String sourceType) {
        for (String documentId : jdbc.queryForList(
                "SELECT document_id FROM policy_document WHERE source_type = ?", String.class, sourceType)) {
            for (String versionId : jdbc.queryForList(
                    "SELECT version_id FROM policy_document_version WHERE document_id = ?", String.class, documentId)) {
                jdbc.update("DELETE FROM policy_chunk WHERE version_id = ?", versionId);
            }
            jdbc.update("DELETE FROM policy_document_version WHERE document_id = ?", documentId);
            jdbc.update("DELETE FROM policy_document WHERE document_id = ?", documentId);
        }
    }

    private void writeReports(EvalReport report) throws Exception {
        Path resultsDirectory = Path.of("evaluation/results");
        Files.createDirectories(resultsDirectory);
        ObjectMapper writer = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        writer.writerWithDefaultPrettyPrinter().writeValue(
                resultsDirectory.resolve("policy-rag-" + report.label() + ".json").toFile(), report);
        Files.writeString(resultsDirectory.resolve("policy-rag-" + report.label() + ".md"), markdown(report));
    }

    private static String markdown(EvalReport report) {
        Metrics m = report.metrics();
        StringBuilder out = new StringBuilder("# Policy RAG " + report.label().toUpperCase(Locale.ROOT) + "\n\n")
                .append("- Git HEAD: `").append(report.gitHead()).append("`\n")
                .append("- Corpus: `").append(report.corpusIdentifier()).append("`\n")
                .append("- Corpus SHA-256: `").append(report.corpusHash()).append("`\n")
                .append("- Embedding: `").append(report.embeddingModel()).append("` / ").append(report.embeddingDimension()).append(" dimensions\n")
                .append("- Retrieval: ").append(report.strategy()).append(", topK=").append(report.topK())
                .append(", threshold=").append(report.threshold()).append("\n")
                .append("- Cases: ").append(report.caseCount()).append(", corpus chunks: ").append(report.corpusChunkCount()).append("\n\n")
                .append("| Metric | Value |\n|---|---:|\n")
                .append(row("Router Accuracy", m.routerAccuracy()))
                .append(row("Policy-required Recall", m.policyRequiredRecall()))
                .append(row("Recall@1", m.recallAt1())).append(row("Recall@3", m.recallAt3()))
                .append(row("Recall@5", m.recallAt5())).append(row("MRR", m.mrr()))
                .append(row("ExactReferenceAccuracy", m.exactReferenceAccuracy()))
                .append(row("TemporalVersionAccuracy", m.temporalVersionAccuracy()))
                .append(row("NoMatchAccuracy", m.noMatchAccuracy()))
                .append("| FalseMatchCount | ").append(m.falseMatchCount()).append(" |\n")
                .append("| Retrieval latency p50 | ").append(m.retrievalLatencyP50Ms()).append(" ms |\n")
                .append("| Retrieval latency p95 | ").append(m.retrievalLatencyP95Ms()).append(" ms |\n\n")
                .append("## Router confusion matrix\n\n| Actual \\ Predicted | N | S | R |\n|---|---:|---:|---:|\n");
        for (String actual : List.of("NOT_REQUIRED", "SUPPLEMENTAL", "REQUIRED")) {
            Map<String, Integer> values = report.routerConfusion().get(actual);
            out.append("| ").append(shortName(actual)).append(" | ").append(values.get("NOT_REQUIRED"))
                    .append(" | ").append(values.get("SUPPLEMENTAL")).append(" | ")
                    .append(values.get("REQUIRED")).append(" |\n");
        }
        out.append("\n## Failed cases\n\n");
        if (report.failedCaseIds().isEmpty()) out.append("None.\n");
        for (CaseResult result : report.results()) {
            if (!result.failures().isEmpty()) {
                out.append("- `").append(result.id()).append("`: ").append(String.join(", ", result.failures()))
                        .append("; goldRank=").append(result.goldRank()).append("\n");
            }
        }
        out.append("\n## Per-case retrieval\n\n| Case | Decision | Status | Gold rank | Latency ms | Top hit |\n|---|---|---|---:|---:|---|\n");
        for (CaseResult result : report.results()) {
            String top = result.hits().isEmpty() ? "-" : result.hits().getFirst().documentNumber()
                    + " " + result.hits().getFirst().articleNo() + " (" + format(result.hits().getFirst().score()) + ")";
            out.append("| ").append(result.id()).append(" | ").append(result.actualDecision()).append(" | ")
                    .append(result.actualRetrievalStatus()).append(" | ").append(result.goldRank()).append(" | ")
                    .append(result.durationMs()).append(" | ").append(top).append(" |\n");
        }
        return out.toString();
    }

    private static List<ConversationHistoryMessage> history(EvalCase evalCase) {
        List<ConversationHistoryMessage> history = new ArrayList<>();
        for (int index = 0; index < evalCase.turns().size() - 1; index++) {
            history.add(new ConversationHistoryMessage(index * 2 + 1, "USER", evalCase.turns().get(index).message()));
        }
        return history;
    }

    private static int goldRank(EvalCase evalCase, List<RetrievalHit> hits) {
        for (int index = 0; index < hits.size(); index++) {
            RetrievalHit hit = hits.get(index);
            if (evalCase.expectedDocumentNumber().equals(hit.match().version().documentNumber())
                    && evalCase.expectedArticleNo().equals(hit.match().chunk().articleNo())) return index + 1;
        }
        return 0;
    }

    private static List<CaseResult> byCategory(List<CaseResult> results, String category) {
        return results.stream().filter(result -> category.equals(result.category())).toList();
    }

    private static double recall(List<CaseResult> results, int k) {
        return ratio(results.stream().filter(result -> result.goldRank() > 0 && result.goldRank() <= k).count(), results.size());
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private static long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) return 0;
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(index);
    }

    private static Map<String, Map<String, Integer>> confusion(List<CaseResult> results) {
        Map<String, Map<String, Integer>> matrix = new LinkedHashMap<>();
        for (String actual : List.of("NOT_REQUIRED", "SUPPLEMENTAL", "REQUIRED")) {
            Map<String, Integer> row = new LinkedHashMap<>();
            for (String predicted : List.of("NOT_REQUIRED", "SUPPLEMENTAL", "REQUIRED")) row.put(predicted, 0);
            matrix.put(actual, row);
        }
        for (CaseResult result : results) {
            Map<String, Integer> row = matrix.get(result.expectedDecision());
            row.put(result.actualDecision(), row.get(result.actualDecision()) + 1);
        }
        return matrix;
    }

    private static String row(String name, double value) {
        return "| " + name + " | " + format(value) + " |\n";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String shortName(String decision) {
        return switch (decision) {
            case "NOT_REQUIRED" -> "N";
            case "SUPPLEMENTAL" -> "S";
            default -> "R";
        };
    }

    record EvalCorpus(String identifier, String sourceType, List<CorpusDocument> documents) {}
    record CorpusDocument(String title, String documentType, String issuer, String jurisdiction,
                          List<CorpusVersion> versions) {}
    record CorpusVersion(String versionLabel, String documentNumber, String effectiveFrom,
                         String effectiveTo, String sourceUri, String structuredText) {}
    record EvalDataset(int schemaVersion, String name, List<EvalCase> cases) {
        EvalCase caseById(String id) { return cases.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(); }
    }
    record EvalCase(String id, String category, List<EvalTurn> turns, String asOfDate,
                    String expectedDecision, String expectedRetrievalStatus,
                    String expectedDocumentNumber, String expectedArticleNo,
                    List<String> expectedToolNames, Boolean expectedAbstention, Boolean manualReview) {}
    record EvalTurn(String message) {}
    record HitResult(int rank, String documentNumber, String versionLabel, String articleNo,
                     String matchType, double score, String content) {
        static HitResult from(int rank, RetrievalHit hit) {
            return new HitResult(rank, hit.match().version().documentNumber(), hit.match().version().versionLabel(),
                    hit.match().chunk().articleNo(), hit.matchType().name(), hit.score(), hit.match().chunk().content());
        }
    }
    record CaseResult(String id, String category, String expectedDecision, String actualDecision,
                      String expectedRetrievalStatus, String actualRetrievalStatus, int goldRank,
                      boolean temporalCorrect, long durationMs, List<String> failures, List<HitResult> hits) {}
    record Metrics(double routerAccuracy, double policyRequiredRecall, double recallAt1, double recallAt3,
                   double recallAt5, double mrr, double exactReferenceAccuracy, double temporalVersionAccuracy,
                   double noMatchAccuracy, long falseMatchCount, long retrievalLatencyP50Ms,
                   long retrievalLatencyP95Ms) {}
    record EvalReport(String label, String gitHead, String corpusIdentifier, String corpusHash,
                      String embeddingModel, int embeddingDimension, int topK, double threshold,
                      String strategy, int caseCount, int corpusChunkCount, Metrics metrics,
                      Map<String, Map<String, Integer>> routerConfusion, List<String> failedCaseIds,
                      List<CaseResult> results) {}
}

