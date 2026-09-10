package com.loanops.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanops.config.LoanOpsAgentProperties;
import com.loanops.dto.AgentAuditResponse;
import com.loanops.dto.AgentChatResult;
import com.loanops.policy.PolicyIngestionService;
import com.loanops.policy.PolicyIndexRebuilder;
import com.loanops.policy.PolicyTypes.DocumentInput;
import com.loanops.policy.PolicyTypes.IngestionResult;
import com.loanops.policy.PolicyTypes.VersionInput;
import com.loanops.policy.PolicyTypes.VersionStatus;
import com.loanops.policy.PolicyVectorIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "POLICY_AGENT_REAL_E2E_TEST", matches = "true")
@SpringBootTest(properties = {
        "loanops.business-date=2026-08-23",
        "loanops.policy.qdrant.collection=loanops_policy_agent_real_e2e"
})
@ActiveProfiles({"mysql", "policy", "ai"})
class PolicyAgentRealE2EIntegrationTest {

    private static final String TITLE = "贷后管理评测规程";
    private static final String SOURCE_TYPE = "DEMO_SYNTHETIC";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private PolicyIngestionService ingestionService;
    @Autowired private PolicyIndexRebuilder indexRebuilder;
    @Autowired private PolicyVectorIndex vectorIndex;
    @Autowired private LoanOpsAgentService agentService;
    @Autowired private com.loanops.audit.AgentAuditService agentAuditService;
    @Autowired private LoanOpsAgentProperties agentProperties;
    @Autowired private JdbcTemplate jdbc;

    private final List<String> requestIds = new ArrayList<>();
    private String conversationId;
    private String documentId;

    @Test
    void realMysqlBgeQdrantChatToolsConversationAndAuditGate() throws Exception {
        cleanupFixture();
        vectorIndex.replaceAll(List.of());
        JsonNode corpus = objectMapper.readTree(
                new org.springframework.core.io.ClassPathResource("policy/demo-policy-corpus.json").getInputStream());
        JsonNode document = corpus.path("documents").get(0);
        JsonNode version = document.path("versions").get(1);
        LocalDate effectiveFrom = LocalDate.parse(version.path("effectiveFrom").asText());
        IngestionResult ingestion = ingestionService.ingest(
                new DocumentInput(TITLE, document.path("documentType").asText(),
                        document.path("issuer").asText(), SOURCE_TYPE, document.path("jurisdiction").asText()),
                new VersionInput(version.path("versionLabel").asText(), version.path("documentNumber").asText(),
                        effectiveFrom, effectiveFrom, effectiveFrom, null, VersionStatus.ACTIVE,
                        version.path("sourceUri").asText(), null, version.path("structuredText").asText()));
        documentId = ingestion.documentId();
        assertThat(indexRebuilder.rebuild()).isGreaterThanOrEqualTo(2);

        String firstRequestId = UUID.randomUUID().toString();
        requestIds.add(firstRequestId);
        AgentChatResult first = agentService.chatWithRequestId(firstRequestId, null,
                "LN-10002 为什么逾期？");
        conversationId = first.conversationId();

        String secondRequestId = UUID.randomUUID().toString();
        requestIds.add(secondRequestId);
        AgentChatResult second = agentService.chatWithRequestId(secondRequestId, conversationId,
                "按照规定现在应该怎么处理？");

        AgentAuditResponse firstAudit = agentAuditService.get(firstRequestId);
        AgentAuditResponse secondAudit = agentAuditService.get(secondRequestId);
        assertThat(firstAudit.status()).isEqualTo("SUCCESS");
        assertThat(secondAudit.status()).isEqualTo("SUCCESS");
        assertThat(firstAudit.provider()).isEqualTo(agentProperties.provider());
        assertThat(firstAudit.model()).isEqualTo(agentProperties.model());
        assertThat(secondAudit.provider()).isEqualTo(agentProperties.provider());
        assertThat(secondAudit.model()).isEqualTo(agentProperties.model());
        assertThat(firstAudit.tools()).anySatisfy(tool -> {
            assertThat(tool.toolName()).isEqualTo("getOverdueDiagnosis");
            assertThat(tool.loanNo()).isEqualTo("LN-10002");
            assertThat(tool.status()).isEqualTo("SUCCESS");
        });
        assertThat(second.answer()).contains("[P1]");

        java.util.Map<String, Object> retrieval = jdbc.queryForMap("""
                SELECT decision, status, as_of_date, query_hash, context_hash,
                       retrieval_config_hash, embedding_model, index_collection, error_type
                FROM policy_retrieval_audit WHERE request_id = ?
                """, secondRequestId);
        assertThat(retrieval.get("decision")).isEqualTo("SUPPLEMENTAL");
        assertThat(retrieval.get("status")).isEqualTo("MATCHED");
        assertThat(retrieval.get("query_hash").toString()).hasSize(64);
        assertThat(retrieval.get("context_hash").toString()).hasSize(64);
        assertThat(retrieval.get("retrieval_config_hash").toString()).hasSize(64);
        assertThat(retrieval.get("embedding_model")).isEqualTo("bge-m3");
        assertThat(retrieval.get("index_collection")).isEqualTo("loanops_policy_agent_real_e2e");
        assertThat(retrieval.get("error_type")).isNull();

        java.util.Map<String, Object> hit = jdbc.queryForMap("""
                SELECT h.rank_no, h.chunk_id, h.version_id, h.match_type, h.score,
                       h.selected_for_context, h.citation_ref, h.cited_in_answer, c.article_no
                FROM policy_retrieval_hit h
                JOIN policy_retrieval_audit a ON a.retrieval_id = h.retrieval_id
                JOIN policy_chunk c ON c.chunk_id = h.chunk_id
                WHERE a.request_id = ? AND h.selected_for_context = TRUE
                ORDER BY h.rank_no LIMIT 1
                """, secondRequestId);
        assertThat(hit.get("version_id")).isEqualTo(ingestion.versionId());
        assertThat(hit.get("article_no")).isEqualTo("第四十四条");
        assertThat(hit.get("citation_ref")).isEqualTo("P1");
        assertThat(hit.get("cited_in_answer")).isEqualTo(true);

        List<java.util.Map<String, Object>> messages = jdbc.queryForList("""
                SELECT sequence_no, role, content, request_id
                FROM conversation_message WHERE conversation_id = ? ORDER BY sequence_no
                """, conversationId);
        assertThat(messages).hasSize(4);
        assertThat(messages).extracting(message -> message.get("role"))
                .containsExactly("USER", "ASSISTANT", "USER", "ASSISTANT");
        assertThat(messages.get(0).get("content")).isEqualTo("LN-10002 为什么逾期？");
        assertThat(messages.get(2).get("content")).isEqualTo("按照规定现在应该怎么处理？");

        System.out.printf("POLICY_AGENT_E2E firstRequest=%s secondRequest=%s conversation=%s%n",
                firstRequestId, secondRequestId, conversationId);
        System.out.printf("POLICY_AGENT_E2E_TOOL name=%s loanNo=%s status=%s%n",
                firstAudit.tools().getFirst().toolName(), firstAudit.tools().getFirst().loanNo(),
                firstAudit.tools().getFirst().status());
        System.out.printf("POLICY_AGENT_E2E_POLICY version=%s article=%s chunk=%s score=%s citation=%s cited=%s%n",
                hit.get("version_id"), hit.get("article_no"), hit.get("chunk_id"), hit.get("score"),
                hit.get("citation_ref"), hit.get("cited_in_answer"));
        System.out.printf("POLICY_AGENT_E2E_AUDIT first=%s second=%s policy=%s transcriptRoles=USER,ASSISTANT,USER,ASSISTANT%n",
                firstAudit.status(), secondAudit.status(), retrieval.get("status"));
        System.out.printf("POLICY_AGENT_E2E_IDENTITY provider=%s model=%s embedding=%s%n",
                secondAudit.provider(), secondAudit.model(), retrieval.get("embedding_model"));
        System.out.println("POLICY_AGENT_E2E_ANSWER=" + second.answer().replaceAll("\\s+", " "));
    }

    @AfterEach
    void cleanup() {
        vectorIndex.replaceAll(List.of());
        for (String requestId : requestIds) {
            jdbc.update("DELETE FROM policy_retrieval_hit WHERE retrieval_id IN "
                    + "(SELECT retrieval_id FROM policy_retrieval_audit WHERE request_id = ?)", requestId);
            jdbc.update("DELETE FROM policy_retrieval_audit WHERE request_id = ?", requestId);
        }
        if (conversationId != null) jdbc.update("DELETE FROM conversation_message WHERE conversation_id = ?", conversationId);
        for (String requestId : requestIds) {
            jdbc.update("DELETE FROM agent_tool_audit_log WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM agent_audit_log WHERE request_id = ?", requestId);
        }
        if (conversationId != null) jdbc.update("DELETE FROM conversation WHERE conversation_id = ?", conversationId);
        cleanupFixture();
    }

    private void cleanupFixture() {
        List<String> documents = jdbc.queryForList(
                "SELECT document_id FROM policy_document WHERE title = ? AND source_type = ?",
                String.class, TITLE, SOURCE_TYPE);
        for (String fixtureDocumentId : documents) {
            List<String> versions = jdbc.queryForList(
                    "SELECT version_id FROM policy_document_version WHERE document_id = ?",
                    String.class, fixtureDocumentId);
            for (String versionId : versions) jdbc.update("DELETE FROM policy_chunk WHERE version_id = ?", versionId);
            jdbc.update("DELETE FROM policy_document_version WHERE document_id = ?", fixtureDocumentId);
            jdbc.update("DELETE FROM policy_document WHERE document_id = ?", fixtureDocumentId);
        }
    }
}
