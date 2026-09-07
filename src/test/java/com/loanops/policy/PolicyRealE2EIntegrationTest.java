package com.loanops.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanops.policy.PolicyTypes.DocumentInput;
import com.loanops.policy.PolicyTypes.IngestionResult;
import com.loanops.policy.PolicyTypes.RetrievalHit;
import com.loanops.policy.PolicyTypes.RetrievalResult;
import com.loanops.policy.PolicyTypes.VersionInput;
import com.loanops.policy.PolicyTypes.VersionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "POLICY_REAL_E2E_TEST", matches = "true")
@SpringBootTest(properties = {
        "loanops.policy.qdrant.collection=loanops_policy_chunks_real_e2e",
        "loanops.policy.retrieval-top-k=5"
})
@ActiveProfiles({"mysql", "policy"})
class PolicyRealE2EIntegrationTest {

    private static final String COLLECTION = "loanops_policy_chunks_real_e2e";
    private static final String SOURCE_TYPE = "DEMO_SYNTHETIC";
    private static final String DOCUMENT_TITLE = "Phase 7.2 E2E 合成政策测试文档";
    private static final LocalDate OLD_DATE = LocalDate.of(2025, 6, 1);
    private static final LocalDate NEW_DATE = LocalDate.of(2026, 6, 1);

    private static final String VERSION_A_TEXT = """
            第六章 贷后管理
            第四十四条 贷款未按照借款合同约定偿还的，贷款人应当采取清收、协议重组、债权转让或者核销等方式进行处置。
            第四十五条 贷款全部还清后，贷款人应当出具结清确认，办理相关解押手续，并妥善保存还款记录。
            """;

    private static final String VERSION_B_TEXT = """
            第六章 贷后管理
            第四十四条 贷款发生逾期后，贷款人应当依法采取催收、协议重组、债权转让或者核销等方式进行处置。
            第四十五条 贷款全部清偿后，贷款人应当及时出具结清证明，办理相关解押手续，并妥善保存还款凭证。
            """;

    @Autowired private PolicyIngestionService ingestionService;
    @Autowired private PolicyIndexRebuilder indexRebuilder;
    @Autowired private PolicyRetriever retriever;
    @Autowired private PolicyEmbeddingProvider embeddingProvider;
    @Autowired private PolicyVectorIndex vectorIndex;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestClient.Builder restClientBuilder;

    @Test
    void realMysqlOllamaQdrantRetrievalAndRebuildGate() throws Exception {
        assertThat(embeddingProvider).isInstanceOf(SpringAiPolicyEmbeddingProvider.class);
        assertThat(vectorIndex).isInstanceOf(QdrantPolicyVectorIndex.class);
        cleanupFixture();
        vectorIndex.replaceAll(List.of());

        DocumentInput document = new DocumentInput(
                DOCUMENT_TITLE, "DEMO_POLICY", "LoanOps Demo",
                SOURCE_TYPE, "CN");
        VersionInput versionA = version("2025版", "DEMO-E2E-2025", LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1), VERSION_A_TEXT);
        VersionInput versionB = version("2026版", "DEMO-E2E-2026", LocalDate.of(2026, 1, 1),
                null, VERSION_B_TEXT);

        try {
            IngestionResult firstA = ingestionService.ingest(document, versionA);
            IngestionResult firstB = ingestionService.ingest(document, versionB);
            int documentsBeforeRepeat = count("policy_document", "document_id", firstA.documentId());
            int versionsBeforeRepeat = count("policy_document_version", "document_id", firstA.documentId());
            int chunksBeforeRepeat = countChunks(firstA.documentId());

            IngestionResult repeatedA = ingestionService.ingest(document, versionA);
            IngestionResult repeatedB = ingestionService.ingest(document, versionB);

            assertThat(repeatedA).isEqualTo(firstA);
            assertThat(repeatedB).isEqualTo(firstB);
            assertThat(count("policy_document", "document_id", firstA.documentId()))
                    .isEqualTo(documentsBeforeRepeat).isEqualTo(1);
            assertThat(count("policy_document_version", "document_id", firstA.documentId()))
                    .isEqualTo(versionsBeforeRepeat).isEqualTo(2);
            assertThat(countChunks(firstA.documentId())).isEqualTo(chunksBeforeRepeat).isEqualTo(4);
            assertStoredMetadata(firstA, firstB);

            long embeddingStarted = System.nanoTime();
            float[] probeEmbedding = embeddingProvider.embed(List.of("贷款逾期处置措施")).getFirst();
            Duration embeddingDuration = elapsed(embeddingStarted);
            assertThat(probeEmbedding).hasSize(1024);

            long rebuildStarted = System.nanoTime();
            int rebuilt = indexRebuilder.rebuild();
            Duration rebuildDuration = elapsed(rebuildStarted);
            assertThat(rebuilt).isEqualTo(chunksBeforeRepeat);
            assertQdrantState(1024, chunksBeforeRepeat, mysqlChunkIds(firstA.documentId()));
            assertQdrantDateFilter(probeEmbedding, firstA.versionId(), firstB.versionId());

            TimedRetrieval queryA = retrieveTimed("贷款逾期以后银行应该采取什么措施？", NEW_DATE);
            TimedRetrieval queryB = retrieveTimed("贷款全部还清以后如何办理结清证明和解押手续？", NEW_DATE);
            assertThat(queryA.result().hits()).isNotEmpty();
            assertThat(queryB.result().hits()).isNotEmpty();
            assertThat(queryA.result().hits().getFirst().match().chunk().articleNo()).isEqualTo("第四十四条");
            assertThat(queryB.result().hits().getFirst().match().chunk().articleNo()).isEqualTo("第四十五条");
            assertThat(queryA.result().hits().getFirst().match().chunk().chunkId())
                    .isNotEqualTo(queryB.result().hits().getFirst().match().chunk().chunkId());

            RetrievalResult exact = retriever.retrieve("第四十四条", NEW_DATE);
            assertThat(exact.hits()).isNotEmpty();
            assertThat(exact.hits().getFirst().match().chunk().articleNo()).isEqualTo("第四十四条");
            assertThat(exact.hits()).extracting(hit -> hit.match().chunk().chunkId()).doesNotHaveDuplicates();

            RetrievalResult oldResult = retriever.retrieve("贷款逾期以后银行应该采取什么措施？", OLD_DATE);
            RetrievalResult newResult = queryA.result();
            assertOnlyVersion(oldResult, firstA.versionId());
            assertOnlyVersion(newResult, firstB.versionId());

            vectorIndex.replaceAll(List.of());
            assertCollectionMissing();
            long secondRebuildStarted = System.nanoTime();
            int rebuiltAgain = indexRebuilder.rebuild();
            Duration secondRebuildDuration = elapsed(secondRebuildStarted);
            assertThat(rebuiltAgain).isEqualTo(chunksBeforeRepeat);
            assertQdrantState(1024, chunksBeforeRepeat, mysqlChunkIds(firstA.documentId()));
            RetrievalResult afterRebuild = retriever.retrieve(
                    "贷款逾期以后银行应该采取什么措施？", NEW_DATE);
            assertThat(afterRebuild.hits().getFirst().match().chunk().articleNo()).isEqualTo("第四十四条");

            System.out.printf("POLICY_E2E_COUNTS document=1 versions=2 chunks=%d repeatedIngestUnchanged=true%n",
                    chunksBeforeRepeat);
            System.out.printf("POLICY_E2E_EMBEDDING provider=%s model=bge-m3 dimensions=%d durationMs=%d%n",
                    embeddingProvider.getClass().getSimpleName(), probeEmbedding.length, embeddingDuration.toMillis());
            System.out.printf("POLICY_E2E_REBUILD entries=%d durationMs=%d secondDurationMs=%d%n",
                    rebuilt, rebuildDuration.toMillis(), secondRebuildDuration.toMillis());
            printRetrieval("QUERY_A", queryA);
            printRetrieval("QUERY_B", queryB);
            printRetrieval("EXACT", new TimedRetrieval(exact, Duration.ZERO));
            System.out.printf("POLICY_E2E_TEMPORAL asOf=%s version=%s; asOf=%s version=%s%n",
                    OLD_DATE, firstA.versionId(), NEW_DATE, firstB.versionId());
            System.out.println("POLICY_E2E_REBUILD_GATE collectionDeleted=true restoredFromMysql=true queryAArticle=第四十四条");
        } finally {
            vectorIndex.replaceAll(List.of());
            cleanupFixture();
        }
    }

    private VersionInput version(String label, String number, LocalDate from, LocalDate to, String text) {
        return new VersionInput(label, number, from.minusDays(30), from.minusDays(15), from, to,
                VersionStatus.ACTIVE, "urn:loanops:demo:phase-7.2-e2e:" + label, null, text);
    }

    private TimedRetrieval retrieveTimed(String query, LocalDate asOfDate) {
        long started = System.nanoTime();
        RetrievalResult result = retriever.retrieve(query, asOfDate);
        return new TimedRetrieval(result, elapsed(started));
    }

    private void assertStoredMetadata(IngestionResult versionA, IngestionResult versionB) {
        List<String> articles = jdbc.queryForList("""
                SELECT c.article_no
                FROM policy_chunk c
                JOIN policy_document_version v ON v.version_id = c.version_id
                WHERE v.document_id = ?
                ORDER BY v.effective_from, c.ordinal
                """, String.class, versionA.documentId());
        assertThat(articles).containsExactly("第四十四条", "第四十五条", "第四十四条", "第四十五条");
        assertThat(jdbc.queryForList("""
                SELECT content_hash FROM policy_chunk
                WHERE version_id IN (?, ?)
                """, String.class, versionA.versionId(), versionB.versionId()))
                .allSatisfy(hash -> assertThat(hash).matches("[0-9a-f]{64}"));
    }

    private void assertOnlyVersion(RetrievalResult result, String expectedVersionId) {
        assertThat(result.hits()).isNotEmpty();
        assertThat(result.hits()).allSatisfy(hit ->
                assertThat(hit.match().version().versionId()).isEqualTo(expectedVersionId));
    }

    private void assertQdrantDateFilter(float[] queryEmbedding, String oldVersionId, String newVersionId) {
        Set<String> oldIds = new HashSet<>(jdbc.queryForList(
                "SELECT chunk_id FROM policy_chunk WHERE version_id = ?", String.class, oldVersionId));
        Set<String> newIds = new HashSet<>(jdbc.queryForList(
                "SELECT chunk_id FROM policy_chunk WHERE version_id = ?", String.class, newVersionId));
        assertThat(vectorIndex.search(queryEmbedding, OLD_DATE, 10))
                .isNotEmpty()
                .allSatisfy(hit -> assertThat(oldIds).contains(hit.chunkId()));
        assertThat(vectorIndex.search(queryEmbedding, NEW_DATE, 10))
                .isNotEmpty()
                .allSatisfy(hit -> assertThat(newIds).contains(hit.chunkId()));
    }

    private void assertQdrantState(int dimensions, int points, Set<String> expectedIds) throws Exception {
        RestClient client = restClientBuilder.baseUrl("http://127.0.0.1:6333").build();
        JsonNode collection = objectMapper.readTree(client.get()
                .uri("/collections/{collection}", COLLECTION).retrieve().body(String.class));
        assertThat(collection.path("result").path("config").path("params").path("vectors").path("size").asInt())
                .isEqualTo(dimensions);
        assertThat(collection.path("result").path("points_count").asInt()).isEqualTo(points);

        JsonNode scroll = objectMapper.readTree(client.post()
                .uri("/collections/{collection}/points/scroll", COLLECTION)
                .body(java.util.Map.of("limit", 100, "with_payload", false, "with_vector", false))
                .retrieve().body(String.class));
        Set<String> qdrantIds = new HashSet<>();
        scroll.path("result").path("points").forEach(point -> qdrantIds.add(point.path("id").asText()));
        assertThat(qdrantIds).isEqualTo(expectedIds);
    }

    private void assertCollectionMissing() {
        RestClient client = restClientBuilder.baseUrl("http://127.0.0.1:6333").build();
        assertThat(client.get().uri("/collections/{collection}/exists", COLLECTION)
                .retrieve().body(String.class)).contains("\"exists\":false");
    }

    private Set<String> mysqlChunkIds(String documentId) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT c.chunk_id
                FROM policy_chunk c
                JOIN policy_document_version v ON v.version_id = c.version_id
                WHERE v.document_id = ?
                """, String.class, documentId));
    }

    private int countChunks(String documentId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM policy_chunk c
                JOIN policy_document_version v ON v.version_id = c.version_id
                WHERE v.document_id = ?
                """, Integer.class, documentId);
        return count == null ? 0 : count;
    }

    private int count(String table, String column, String value) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
        return count == null ? 0 : count;
    }

    private void cleanupFixture() {
        List<String> documentIds = jdbc.queryForList(
                "SELECT document_id FROM policy_document WHERE source_type = ? AND title = ?",
                String.class, SOURCE_TYPE, DOCUMENT_TITLE);
        for (String documentId : documentIds) {
            List<String> versionIds = jdbc.queryForList(
                    "SELECT version_id FROM policy_document_version WHERE document_id = ?", String.class, documentId);
            for (String versionId : versionIds) {
                jdbc.update("DELETE FROM policy_chunk WHERE version_id = ?", versionId);
            }
            jdbc.update("DELETE FROM policy_document_version WHERE document_id = ?", documentId);
            jdbc.update("DELETE FROM policy_document WHERE document_id = ?", documentId);
        }
    }

    private static Duration elapsed(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }

    private static void printRetrieval(String label, TimedRetrieval timed) {
        System.out.printf("POLICY_E2E_%s durationMs=%d topK=%d%n",
                label, timed.duration().toMillis(), timed.result().hits().size());
        for (int index = 0; index < timed.result().hits().size(); index++) {
            RetrievalHit hit = timed.result().hits().get(index);
            String preview = hit.match().chunk().content().replaceAll("\\s+", " ");
            if (preview.length() > 100) preview = preview.substring(0, 100);
            System.out.printf("  rank=%d chunkId=%s document=%s version=%s article=%s match=%s score=%.6f preview=%s%n",
                    index + 1, hit.match().chunk().chunkId(), hit.match().document().title(),
                    hit.match().version().versionLabel(), hit.match().chunk().articleNo(), hit.matchType(),
                    hit.score(), preview);
        }
    }

    private record TimedRetrieval(RetrievalResult result, Duration duration) {}
}
