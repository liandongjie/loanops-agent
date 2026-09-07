package com.loanops.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loanops.audit.AgentAuditHandle;
import com.loanops.audit.AgentAuditService;
import com.loanops.audit.AuditContentPolicy;
import com.loanops.conversation.ConversationHistoryFingerprint;
import com.loanops.conversation.ConversationHistoryMessage;
import com.loanops.conversation.ConversationSnapshot;
import com.loanops.conversation.ConversationTurnStore;
import com.loanops.dto.AgentAuditResponse;
import com.loanops.dto.AgentChatResult;
import com.loanops.exception.ConversationConflictException;
import com.loanops.exception.ConversationNotFoundException;
import com.loanops.exception.LoanNotFoundException;
import com.loanops.persistence.entity.AgentAuditLogEntity;
import com.loanops.persistence.entity.ConversationMessageEntity;
import com.loanops.persistence.mapper.AgentAuditLogMapper;
import com.loanops.persistence.mapper.ConversationMessageMapper;
import com.loanops.tool.LoanOpsTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.ai.deepseek.api-key=test-key",
        "loanops.business-date=2026-09-05",
        "loanops.conversation.history-message-limit=4"
})
@ActiveProfiles("ai")
@AutoConfigureMockMvc
class AgentConversationRuntimeIntegrationTest {

    private static final String SYSTEM_PROMPT = "test system prompt requiring current facts from tools";

    @MockitoBean
    private AgentChatGateway gateway;

    @Autowired
    private LoanOpsAgentService service;

    @Autowired
    private ConversationTurnStore turnStore;

    @Autowired
    private AgentTurnCompletionService completionService;

    @Autowired
    private AgentAuditService auditService;

    @Autowired
    private AuditContentPolicy contentPolicy;

    @Autowired
    private AgentAuditLogMapper auditMapper;

    @Autowired
    private ConversationMessageMapper messageMapper;

    @Autowired
    private LoanOpsTools tools;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanAndConfigureGateway() {
        jdbcTemplate.update("DELETE FROM policy_retrieval_hit");
        jdbcTemplate.update("DELETE FROM policy_retrieval_audit");
        jdbcTemplate.update("DELETE FROM agent_tool_audit_log");
        jdbcTemplate.update("DELETE FROM conversation_message");
        jdbcTemplate.update("DELETE FROM agent_audit_log");
        jdbcTemplate.update("DELETE FROM conversation");
        when(gateway.systemPrompt()).thenReturn(SYSTEM_PROMPT);
        when(gateway.chat(any()))
                .thenAnswer(invocation -> "answer:"
                        + invocation.getArgument(0, AgentChatRequest.class).currentUserMessage());
    }

    @Test
    void firstAndSecondSuccessfulTurnsShareDurableHistoryAndExactAuditMetadata() {
        String firstMessage = "LN-10002 为什么逾期？";
        String secondMessage = "那它现在还欠多少？";
        String firstRequestId = UUID.randomUUID().toString();
        String secondRequestId = UUID.randomUUID().toString();

        AgentChatResult first = service.chatWithRequestId(firstRequestId, null, firstMessage);
        ConversationSnapshot afterFirst = turnStore.resolve(first.conversationId());
        AgentChatResult second = service.chatWithRequestId(
                secondRequestId, first.conversationId(), secondMessage);

        assertThat(first.conversationId()).isEqualTo(second.conversationId());
        assertThat(first.requestId()).isEqualTo(firstRequestId);
        assertThat(second.requestId()).isEqualTo(secondRequestId);
        ArgumentCaptor<AgentChatRequest> requests = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(gateway, org.mockito.Mockito.times(2)).chat(requests.capture());
        assertThat(requests.getAllValues().get(0).history()).isEmpty();
        assertThat(requests.getAllValues().get(1).history())
                .extracting(ConversationHistoryMessage::sequenceNo,
                        ConversationHistoryMessage::role,
                        ConversationHistoryMessage::content)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "USER", firstMessage),
                        org.assertj.core.groups.Tuple.tuple(2, "ASSISTANT", first.answer()));

        AgentAuditResponse firstAudit = auditService.get(firstRequestId);
        assertThat(firstAudit.status()).isEqualTo("SUCCESS");
        assertThat(firstAudit.conversationId()).isEqualTo(first.conversationId());
        assertThat(firstAudit.historyFromSequence()).isNull();
        assertThat(firstAudit.historyToSequence()).isNull();
        assertThat(firstAudit.historyHash())
                .isEqualTo(ConversationHistoryFingerprint.sha256(List.of()));
        assertThat(firstAudit.systemPromptHash())
                .isEqualTo(contentPolicy.snapshot(SYSTEM_PROMPT).sha256());

        AgentAuditResponse secondAudit = auditService.get(secondRequestId);
        assertThat(secondAudit.status()).isEqualTo("SUCCESS");
        assertThat(secondAudit.historyFromSequence()).isEqualTo(1);
        assertThat(secondAudit.historyToSequence()).isEqualTo(2);
        assertThat(secondAudit.historyHash()).isEqualTo(afterFirst.historyHash());
        assertThat(secondAudit.systemPromptHash()).isEqualTo(firstAudit.systemPromptHash());

        assertThat(messages(first.conversationId()))
                .extracting(ConversationMessageEntity::getRole)
                .containsExactly("USER", "ASSISTANT", "USER", "ASSISTANT");
    }

    @Test
    void runtimeSuppliesBoundedChronologicalHistoryWithoutCrossConversationLeakage() {
        AgentChatResult first = service.chatWithRequestId(
                UUID.randomUUID().toString(), null, "question-1");
        service.chatWithRequestId(UUID.randomUUID().toString(), first.conversationId(), "question-2");
        service.chatWithRequestId(UUID.randomUUID().toString(), first.conversationId(), "question-3");
        clearInvocations(gateway);

        service.chatWithRequestId(UUID.randomUUID().toString(), first.conversationId(), "question-4");

        ArgumentCaptor<AgentChatRequest> request = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(gateway).chat(request.capture());
        assertThat(request.getValue().currentUserMessage()).isEqualTo("question-4");
        assertThat(request.getValue().history())
                .extracting(ConversationHistoryMessage::sequenceNo)
                .containsExactly(3, 4, 5, 6);

        clearInvocations(gateway);
        service.chatWithRequestId(UUID.randomUUID().toString(), null, "conversation-b");
        verify(gateway).chat(request.capture());
        assertThat(request.getValue().currentUserMessage()).isEqualTo("conversation-b");
        assertThat(request.getValue().history()).isEmpty();
    }

    @Test
    void providerFailureLeavesFailedAuditAndNoConversationMessages() {
        String requestId = UUID.randomUUID().toString();
        RuntimeException providerFailure = new RuntimeException("provider unavailable");
        org.mockito.Mockito.reset(gateway);
        when(gateway.systemPrompt()).thenReturn(SYSTEM_PROMPT);
        when(gateway.chat(any())).thenThrow(providerFailure);

        assertThatThrownBy(() -> service.chatWithRequestId(requestId, null, "question"))
                .isSameAs(providerFailure);

        AgentAuditResponse audit = auditService.get(requestId);
        assertThat(audit.status()).isEqualTo("FAILED");
        assertThat(audit.errorType()).isEqualTo("RuntimeException");
        assertThat(messages(audit.conversationId())).isEmpty();
    }

    @Test
    void toolFailureKeepsRequestCorrelationButAppendsNoConversationMessages() {
        String requestId = UUID.randomUUID().toString();
        org.mockito.Mockito.reset(gateway);
        when(gateway.systemPrompt()).thenReturn(SYSTEM_PROMPT);
        when(gateway.chat(any())).thenAnswer(invocation -> {
            tools.getCurrentRepayment("LN-NOT-FOUND");
            return "unreachable";
        });

        assertThatThrownBy(() -> service.chatWithRequestId(requestId, null, "check missing loan"))
                .isInstanceOf(LoanNotFoundException.class);

        AgentAuditResponse audit = auditService.get(requestId);
        assertThat(audit.status()).isEqualTo("FAILED");
        assertThat(audit.tools()).singleElement().satisfies(tool -> {
            assertThat(tool.toolName()).isEqualTo("getCurrentRepayment");
            assertThat(tool.status()).isEqualTo("FAILED");
        });
        assertThat(messages(audit.conversationId())).isEmpty();
    }

    @Test
    void conflictAfterProviderCompletionPersistsOnlyWinningTurnAndMarksStaleAuditFailed() {
        ConversationSnapshot initial = turnStore.create();
        String staleRequestId = UUID.randomUUID().toString();
        String winningRequestId = UUID.randomUUID().toString();
        org.mockito.Mockito.reset(gateway);
        when(gateway.systemPrompt()).thenReturn(SYSTEM_PROMPT);
        when(gateway.chat(any())).thenAnswer(invocation -> {
            ConversationSnapshot competingSnapshot = turnStore.resolve(initial.conversationId());
            AgentAuditHandle competingAudit = auditService.begin(
                    winningRequestId, "winning question", competingSnapshot, SYSTEM_PROMPT);
            completionService.complete(
                    competingAudit, competingSnapshot, "winning question", "winning answer");
            return "stale answer";
        });

        assertThatThrownBy(() -> service.chatWithRequestId(
                staleRequestId, initial.conversationId(), "stale question"))
                .isInstanceOf(ConversationConflictException.class);

        AgentAuditResponse staleAudit = auditService.get(staleRequestId);
        assertThat(staleAudit.status()).isEqualTo("FAILED");
        assertThat(staleAudit.errorType()).isEqualTo("ConversationConflictException");
        assertThat(auditService.get(winningRequestId).status()).isEqualTo("SUCCESS");
        assertThat(messages(initial.conversationId()))
                .extracting(ConversationMessageEntity::getRequestId,
                        ConversationMessageEntity::getRole,
                        ConversationMessageEntity::getContent)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(winningRequestId, "USER", "winning question"),
                        org.assertj.core.groups.Tuple.tuple(winningRequestId, "ASSISTANT", "winning answer"));
        ConversationSnapshot resolved = turnStore.resolve(initial.conversationId());
        assertThat(resolved.version()).isEqualTo(1);
        assertThat(resolved.lastMessageSequence()).isEqualTo(2);
    }

    @Test
    void unknownConversationDoesNotInvokeGatewayOrCreateAudit() {
        String requestId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> service.chatWithRequestId(
                requestId, UUID.randomUUID().toString(), "question"))
                .isInstanceOf(ConversationNotFoundException.class);

        verifyNoInteractions(gateway);
        assertThat(auditMapper.selectById(requestId)).isNull();
    }

    private List<ConversationMessageEntity> messages(String conversationId) {
        return messageMapper.selectList(new LambdaQueryWrapper<ConversationMessageEntity>()
                .eq(ConversationMessageEntity::getConversationId, conversationId)
                .orderByAsc(ConversationMessageEntity::getSequenceNo));
    }
}
