package com.loanops.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanops.agent.LoanOpsAgentService;
import com.loanops.dto.AgentAuditResponse;
import com.loanops.dto.AgentChatResult;
import com.loanops.policy.PolicyTypes.DocumentInput;
import com.loanops.policy.PolicyTypes.VersionInput;
import com.loanops.policy.PolicyTypes.VersionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "POLICY_RAG_MANUAL_REVIEW", matches = "true")
@SpringBootTest(properties = {
        "loanops.business-date=2026-08-23",
        "loanops.policy.qdrant.collection=loanops_policy_rag_eval"
})
@ActiveProfiles({"mysql", "policy", "ai"})
class PolicyRagManualReviewIntegrationTest {

    private static final Path CASES_PATH = Path.of("evaluation/policy-rag-cases.json");
    private static final Path CORPUS_PATH = Path.of("evaluation/policy-rag-corpus.json");

    @Autowired private ObjectMapper objectMapper;
    @Autowired private PolicyIngestionService ingestionService;
    @Autowired private PolicyIndexRebuilder indexRebuilder;
    @Autowired private PolicyVectorIndex vectorIndex;
    @Autowired private LoanOpsAgentService agentService;
    @Autowired private com.loanops.audit.AgentAuditService agentAuditService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void produceHumanReviewArtifactFromRepresentativeLiveCases() throws Exception {
        JsonNode corpus = objectMapper.readTree(CORPUS_PATH.toFile());
        JsonNode dataset = objectMapper.readTree(CASES_PATH.toFile());
        vectorIndex.replaceAll(List.of());
        try {
            ingest(corpus);
            assertThat(indexRebuilder.rebuild()).isGreaterThanOrEqualTo(30);
            List<JsonNode> reviewCases = new ArrayList<>();
            dataset.path("cases").forEach(item -> {
                if (item.path("manualReview").asBoolean(false)) reviewCases.add(item);
            });
            assertThat(reviewCases).hasSizeBetween(10, 12);

            StringBuilder report = new StringBuilder("# Policy RAG Manual Grounding Review\n\n")
                    .append("Generated from real MySQL + Ollama/BGE-M3 + Qdrant + DeepSeek execution. ")
                    .append("All policy text is `EVALUATION_SYNTHETIC`.\n\n")
                    .append("Review date: 2026-09-07  \n")
                    .append("Business date: 2026-08-23  \n")
                    .append("Cases: ").append(reviewCases.size()).append("\n\n");
            for (JsonNode evalCase : reviewCases) appendCase(report, evalCase);
            Files.writeString(Path.of("evaluation/POLICY_RAG_MANUAL_REVIEW.md"), report);
        } finally {
            vectorIndex.replaceAll(List.of());
        }
    }

    private void appendCase(StringBuilder report, JsonNode evalCase) {
        String conversationId = null;
        List<String> requestIds = new ArrayList<>();
        AgentChatResult finalResult = null;
        for (JsonNode turn : evalCase.path("turns")) {
            String requestId = UUID.randomUUID().toString();
            requestIds.add(requestId);
            finalResult = agentService.chatWithRequestId(requestId, conversationId, turn.path("message").asText());
            conversationId = finalResult.conversationId();
        }
        AgentAuditResponse audit = agentAuditService.get(finalResult.requestId());
        Map<String, Object> retrieval = jdbc.queryForMap("""
                SELECT decision, status, as_of_date, context_hash
                FROM policy_retrieval_audit WHERE request_id = ?
                """, finalResult.requestId());
        List<Map<String, Object>> hits = jdbc.queryForList("""
                SELECT h.rank_no, h.score, h.match_type, h.citation_ref, h.cited_in_answer,
                       d.title, v.document_number, c.article_no, c.content
                FROM policy_retrieval_hit h
                JOIN policy_retrieval_audit a ON a.retrieval_id = h.retrieval_id
                JOIN policy_document d ON d.document_id = h.document_id
                JOIN policy_document_version v ON v.version_id = h.version_id
                JOIN policy_chunk c ON c.chunk_id = h.chunk_id
                WHERE a.request_id = ? AND h.selected_for_context = TRUE
                ORDER BY h.rank_no
                """, finalResult.requestId());

        report.append("## ").append(evalCase.path("id").asText()).append(" — ")
                .append(evalCase.path("category").asText()).append("\n\n")
                .append("**Question**\n\n");
        int turnNo = 0;
        for (JsonNode turn : evalCase.path("turns")) {
            report.append(++turnNo).append(". ").append(turn.path("message").asText()).append("\n");
        }
        report.append("\n**Execution**\n\n")
                .append("- requestId: `").append(finalResult.requestId()).append("`\n")
                .append("- conversationId: `").append(conversationId).append("`\n")
                .append("- Agent Audit: `").append(audit.status()).append("`\n")
                .append("- Policy decision/status: `").append(retrieval.get("decision")).append(" / ")
                .append(retrieval.get("status")).append("`\n")
                .append("- Conversation roles: `").append(conversationRoles(conversationId)).append("`\n\n")
                .append("**Tool Evidence**\n\n");
        List<String> toolEvidence = new ArrayList<>();
        for (String requestId : requestIds) {
            for (var tool : agentAuditService.get(requestId).tools()) {
                toolEvidence.add(tool.toolName() + "(" + tool.loanNo() + ")=" + tool.status());
            }
        }
        report.append(toolEvidence.isEmpty() ? "- None.\n" : "- " + String.join("\n- ", toolEvidence) + "\n");

        report.append("\n**Policy Evidence**\n\n");
        if (hits.isEmpty()) report.append("- None.\n");
        for (Map<String, Object> hit : hits) {
            report.append("- [").append(hit.get("citation_ref")).append("] ")
                    .append(hit.get("document_number")).append(" ").append(hit.get("article_no"))
                    .append("; score=").append(String.format(Locale.ROOT, "%.4f", ((Number) hit.get("score")).doubleValue()))
                    .append("; match=").append(hit.get("match_type"))
                    .append("; cited=").append(hit.get("cited_in_answer")).append("\n  ")
                    .append(hit.get("content")).append("\n");
        }
        report.append("\n**Final Answer**\n\n> ")
                .append(finalResult.answer().replace("\n", "\n> ")).append("\n\n")
                .append("**Citation mapping**\n\n");
        if (hits.isEmpty()) report.append("- No citation mapping recorded.\n");
        for (Map<String, Object> hit : hits) {
            report.append("- [").append(hit.get("citation_ref")).append("] → ")
                    .append(hit.get("document_number")).append(" ").append(hit.get("article_no"))
                    .append("; citedInAnswer=").append(hit.get("cited_in_answer")).append("\n");
        }
        report.append("\n**Human checklist**\n\n")
                .append("- [ ] 当前金融事实与 Tool Evidence 一致或不适用。\n")
                .append("- [ ] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。\n")
                .append("- [ ] Citation mapping 正确或不适用。\n")
                .append("- [ ] 未出现证据之外的额外政策结论。\n")
                .append("- [ ] 已完整回答用户主要问题，或明确记录 Router/知识库限制。\n\n");
    }

    private String conversationRoles(String conversationId) {
        return String.join(",", jdbc.queryForList("""
                SELECT role FROM conversation_message WHERE conversation_id = ? ORDER BY sequence_no
                """, String.class, conversationId));
    }

    private void ingest(JsonNode corpus) {
        String sourceType = corpus.path("sourceType").asText();
        for (JsonNode document : corpus.path("documents")) {
            DocumentInput documentInput = new DocumentInput(document.path("title").asText(),
                    document.path("documentType").asText(), document.path("issuer").asText(),
                    sourceType, document.path("jurisdiction").asText());
            for (JsonNode version : document.path("versions")) {
                LocalDate from = LocalDate.parse(version.path("effectiveFrom").asText());
                String effectiveTo = version.path("effectiveTo").asText(null);
                ingestionService.ingest(documentInput, new VersionInput(version.path("versionLabel").asText(),
                        version.path("documentNumber").asText(), from.minusDays(30), from.minusDays(15), from,
                        effectiveTo == null ? null : LocalDate.parse(effectiveTo), VersionStatus.ACTIVE,
                        version.path("sourceUri").asText(), null, version.path("structuredText").asText()));
            }
        }
    }
}
