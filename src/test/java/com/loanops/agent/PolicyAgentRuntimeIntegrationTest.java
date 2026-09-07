package com.loanops.agent;

import com.loanops.audit.AgentAuditService;
import com.loanops.conversation.ConversationSnapshot;
import com.loanops.conversation.ConversationTurnStore;
import com.loanops.dto.AgentAuditResponse;
import com.loanops.dto.AgentChatResult;
import com.loanops.exception.PolicyCitationValidationException;
import com.loanops.exception.PolicyRetrievalException;
import com.loanops.policy.PolicyGroundingContextFactory;
import com.loanops.policy.PolicyRetriever;
import com.loanops.policy.PolicyTypes;
import com.loanops.policy.PolicyTypes.Chunk;
import com.loanops.policy.PolicyTypes.ChunkType;
import com.loanops.policy.PolicyTypes.Document;
import com.loanops.policy.PolicyTypes.MatchType;
import com.loanops.policy.PolicyTypes.RetrievalHit;
import com.loanops.policy.PolicyTypes.RetrievalResult;
import com.loanops.policy.PolicyTypes.StoredChunk;
import com.loanops.policy.PolicyTypes.Version;
import com.loanops.policy.PolicyTypes.VersionStatus;
import com.loanops.tool.LoanOpsTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.ai.deepseek.api-key=test-key",
        "loanops.business-date=2026-06-01"
})
@ActiveProfiles("ai")
class PolicyAgentRuntimeIntegrationTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 6, 1);
    private static final String SYSTEM_PROMPT = "financial and policy grounding test prompt";

    @MockitoBean private AgentChatGateway gateway;
    @MockitoBean private PolicyRetriever policyRetriever;
    @Autowired private LoanOpsAgentService service;
    @Autowired private ConversationTurnStore turnStore;
    @Autowired private AgentAuditService agentAuditService;
    @Autowired private LoanOpsTools tools;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanAndConfigure() {
        jdbc.update("DELETE FROM policy_retrieval_hit");
        jdbc.update("DELETE FROM policy_retrieval_audit");
        jdbc.update("DELETE FROM agent_tool_audit_log");
        jdbc.update("DELETE FROM conversation_message");
        jdbc.update("DELETE FROM agent_audit_log");
        jdbc.update("DELETE FROM conversation");
        when(gateway.systemPrompt()).thenReturn(SYSTEM_PROMPT);
        when(gateway.chat(any())).thenReturn("普通金融回答");
    }

    @AfterEach
    void cleanAfterTest() {
        jdbc.update("DELETE FROM policy_retrieval_hit");
        jdbc.update("DELETE FROM policy_retrieval_audit");
        jdbc.update("DELETE FROM agent_tool_audit_log");
        jdbc.update("DELETE FROM conversation_message");
        jdbc.update("DELETE FROM agent_audit_log");
        jdbc.update("DELETE FROM conversation");
    }

    @Test
    void pureFinancialCasesSkipPolicyRetriever() {
        AgentChatResult current = service.chatWithRequestId(UUID.randomUUID().toString(), null,
                "LN-10001 当前应该还多少钱？");
        AgentChatResult overdue = service.chatWithRequestId(UUID.randomUUID().toString(), null,
                "LN-10002 为什么逾期？");

        verify(policyRetriever, never()).retrieve(anyString(), any());
        assertThat(policyStatus(current.requestId())).isEqualTo("NOT_RUN");
        assertThat(policyStatus(overdue.requestId())).isEqualTo("NOT_RUN");
    }

    @Test
    void purePolicyMatchIsGroundedAndCited() {
        when(policyRetriever.retrieve(anyString(), eq(AS_OF))).thenReturn(matchedResult());
        when(gateway.chat(any())).thenReturn("应依据命中的条款处理。[P1]");

        AgentChatResult result = service.chatWithRequestId(UUID.randomUUID().toString(), null,
                "个人贷款逾期以后按规定应该如何处置？");

        assertThat(result.answer()).contains("[P1]");
        assertThat(policyDecision(result.requestId())).isEqualTo("REQUIRED");
        assertThat(policyStatus(result.requestId())).isEqualTo("MATCHED");
        assertThat(cited(result.requestId())).isTrue();
    }

    @Test
    void mixedRequestKeepsFinancialToolTruthAndPolicyEvidence() {
        when(policyRetriever.retrieve(anyString(), eq(AS_OF))).thenReturn(matchedResult());
        when(gateway.chat(any())).thenAnswer(invocation -> {
            tools.getOverdueDiagnosis("LN-10002");
            return "该贷款逾期3天，未偿还3500.00元；政策处置依据见[P1]。";
        });

        AgentChatResult result = service.chatWithRequestId(UUID.randomUUID().toString(), null,
                "LN-10002 已经逾期了，按照规定现在应该怎么处理？");

        AgentAuditResponse audit = agentAuditService.get(result.requestId());
        assertThat(policyDecision(result.requestId())).isEqualTo("SUPPLEMENTAL");
        assertThat(audit.tools()).singleElement().satisfies(tool -> {
            assertThat(tool.toolName()).isEqualTo("getOverdueDiagnosis");
            assertThat(tool.loanNo()).isEqualTo("LN-10002");
            assertThat(tool.status()).isEqualTo("SUCCESS");
        });
        assertThat(result.answer()).contains("3500.00", "[P1]");
    }

    @Test
    void multiTurnPolicyQueryUsesPriorUserButNotAssistantAnswer() {
        AgentChatResult first = service.chatWithRequestId(UUID.randomUUID().toString(), null,
                "LN-10002 为什么逾期？");
        clearInvocations(policyRetriever, gateway);
        when(gateway.systemPrompt()).thenReturn(SYSTEM_PROMPT);
        when(policyRetriever.retrieve(anyString(), eq(AS_OF))).thenReturn(matchedResult());
        when(gateway.chat(any())).thenReturn("后续应按政策处理。[P1]");

        AgentChatResult second = service.chatWithRequestId(UUID.randomUUID().toString(), first.conversationId(),
                "那按照规定应该怎么办？");

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(policyRetriever).retrieve(query.capture(), eq(AS_OF));
        assertThat(query.getValue()).contains("LN-10002 为什么逾期？", "那按照规定应该怎么办？")
                .doesNotContain("普通金融回答");
        assertThat(policyDecision(second.requestId())).isEqualTo("SUPPLEMENTAL");
        assertThat(second.answer()).contains("[P1]");
    }

    @Test
    void requiredNoMatchUsesDeterministicFallbackWithoutCallingModel() {
        when(policyRetriever.retrieve(anyString(), eq(AS_OF)))
                .thenReturn(new RetrievalResult("未知政策", AS_OF, List.of()));

        AgentChatResult result = service.chatWithRequestId(UUID.randomUUID().toString(), null,
                "按规定是否允许一个知识库中不存在的操作？");

        verify(gateway, never()).chat(any());
        assertThat(result.answer()).isEqualTo(PolicyGroundingContextFactory.NO_MATCH_NOTICE);
        assertThat(policyStatus(result.requestId())).isEqualTo("NO_MATCH");
        assertThat(agentAuditService.get(result.requestId()).status()).isEqualTo("SUCCESS");
    }

    @Test
    void retrievalFailureFailsClosedForRequiredButAllowsSupplementalFinancialAnswer() {
        RuntimeException qdrantFailure = new RuntimeException("qdrant unavailable");
        when(policyRetriever.retrieve(anyString(), eq(AS_OF))).thenThrow(qdrantFailure);
        String requiredRequestId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> service.chatWithRequestId(requiredRequestId, null,
                "个人贷款按规定应该如何处置？"))
                .isInstanceOf(PolicyRetrievalException.class);
        assertThat(agentAuditService.get(requiredRequestId).status()).isEqualTo("FAILED");
        assertThat(messages(agentAuditService.get(requiredRequestId).conversationId())).isZero();
        assertThat(policyStatus(requiredRequestId)).isEqualTo("FAILED");

        when(gateway.chat(any())).thenAnswer(invocation -> {
            tools.getOverdueDiagnosis("LN-10002");
            return "该贷款未偿还3500.00元。";
        });
        AgentChatResult supplemental = service.chatWithRequestId(UUID.randomUUID().toString(), null,
                "LN-10002 已经逾期，按照规定应该怎么处理？");
        assertThat(supplemental.answer()).contains("3500.00", PolicyGroundingContextFactory.FAILED_NOTICE);
        assertThat(agentAuditService.get(supplemental.requestId()).tools()).singleElement()
                .satisfies(tool -> assertThat(tool.loanNo()).isEqualTo("LN-10002"));
        assertThat(policyStatus(supplemental.requestId())).isEqualTo("FAILED");
    }

    @Test
    void hallucinatedCitationFailsBeforeTranscriptCommit() {
        when(policyRetriever.retrieve(anyString(), eq(AS_OF))).thenReturn(matchedResult());
        when(gateway.chat(any())).thenReturn("伪造引用[P99]");
        String requestId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> service.chatWithRequestId(requestId, null,
                "个人贷款逾期后按规定如何处置？"))
                .isInstanceOf(PolicyCitationValidationException.class);

        AgentAuditResponse audit = agentAuditService.get(requestId);
        assertThat(audit.status()).isEqualTo("FAILED");
        assertThat(messages(audit.conversationId())).isZero();
        assertThat(policyStatus(requestId)).isEqualTo("MATCHED");
        assertThat(cited(requestId)).isFalse();
    }

    private RetrievalResult matchedResult() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        Document document = new Document("document", "合成测试政策", "DEMO_POLICY", "LoanOps Demo",
                "DEMO_SYNTHETIC", "CN", now, now);
        Version version = new Version("version", "document", "2026版", "DEMO-2026",
                null, null, LocalDate.of(2026, 1, 1), null, VersionStatus.ACTIVE,
                "urn:demo", "a".repeat(64), null, now);
        Chunk chunk = new Chunk("chunk", "version", null, ChunkType.ARTICLE,
                "第六章", "贷后管理", "第四十四条", null, null,
                "第六章 贷后管理/第四十四条", 1,
                "第四十四条 贷款逾期后应当依法采取处置措施。", "b".repeat(64), true, now);
        RetrievalHit hit = new RetrievalHit(new StoredChunk(chunk, document, version),
                MatchType.SEMANTIC, 0.91, List.of());
        return new RetrievalResult("query", AS_OF, List.of(hit));
    }

    private String policyDecision(String requestId) {
        return jdbc.queryForObject("SELECT decision FROM policy_retrieval_audit WHERE request_id = ?",
                String.class, requestId);
    }

    private String policyStatus(String requestId) {
        return jdbc.queryForObject("SELECT status FROM policy_retrieval_audit WHERE request_id = ?",
                String.class, requestId);
    }

    private boolean cited(String requestId) {
        Boolean cited = jdbc.queryForObject("""
                SELECT h.cited_in_answer
                FROM policy_retrieval_hit h
                JOIN policy_retrieval_audit a ON a.retrieval_id = h.retrieval_id
                WHERE a.request_id = ? AND h.rank_no = 1
                """, Boolean.class, requestId);
        return Boolean.TRUE.equals(cited);
    }

    private int messages(String conversationId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversation_message WHERE conversation_id = ?", Integer.class, conversationId);
        return count == null ? 0 : count;
    }
}
