package com.loanops.agent;

import com.loanops.audit.AgentAuditHandle;
import com.loanops.audit.AgentAuditService;
import com.loanops.audit.AgentRequestAuditContext;
import com.loanops.config.LoanOpsAgentProperties;
import com.loanops.conversation.ConversationHistoryFingerprint;
import com.loanops.conversation.ConversationSnapshot;
import com.loanops.conversation.ConversationTurnStore;
import com.loanops.dto.AgentChatResult;
import com.loanops.exception.ConversationConflictException;
import com.loanops.exception.ConversationNotFoundException;
import com.loanops.exception.InvalidAgentMessageException;
import com.loanops.observability.AgentMetrics;
import com.loanops.policy.PolicyGroundingContext;
import com.loanops.policy.PolicyRetrievalAuditHandle;
import com.loanops.policy.PolicyRetrievalDecision;
import com.loanops.policy.PolicyRetrievalStatus;
import com.loanops.policy.PolicyRuntimePreparation;
import com.loanops.policy.PolicyRuntimeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.MDC;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoanOpsAgentServiceTest {

    private static final String CONVERSATION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String SYSTEM_PROMPT = "system prompt";
    private static final ConversationSnapshot EMPTY_SNAPSHOT = new ConversationSnapshot(
            CONVERSATION_ID, 0, 0, List.of(), ConversationHistoryFingerprint.sha256(List.of()));
    private static final PolicyRuntimePreparation POLICY_PREPARATION = new PolicyRuntimePreparation(
            new PolicyGroundingContext(PolicyRetrievalDecision.NOT_REQUIRED, PolicyRetrievalStatus.NOT_RUN,
                    LocalDate.of(2026, 1, 1), "hash", List.of(), ""),
            new PolicyRetrievalAuditHandle("retrieval", 1L));

    private final AgentChatGateway gateway = mock(AgentChatGateway.class);
    private final AgentAuditService auditService = mock(AgentAuditService.class);
    private final AgentTurnCompletionService completionService = mock(AgentTurnCompletionService.class);
    private final ConversationTurnStore turnStore = mock(ConversationTurnStore.class);
    private final AgentMetrics metrics = mock(AgentMetrics.class);
    private final PolicyRuntimeService policyRuntimeService = mock(PolicyRuntimeService.class);
    private final LoanOpsAgentService service = new LoanOpsAgentService(
            gateway, auditService, completionService, turnStore, metrics, policyRuntimeService,
            new LoanOpsAgentProperties("deepseek", "deepseek", "deepseek-chat"), 4000);

    @BeforeEach
    void defaults() {
        when(turnStore.create()).thenReturn(EMPTY_SNAPSHOT);
        when(gateway.systemPrompt()).thenReturn(SYSTEM_PROMPT);
        when(policyRuntimeService.prepare(anyString(), eq(EMPTY_SNAPSHOT), anyString()))
                .thenReturn(POLICY_PREPARATION);
        when(policyRuntimeService.validateAndRecord(anyString(), eq(POLICY_PREPARATION)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void oneSnapshotFlowsThroughPolicyModelAndAtomicCompletionWithoutTrimmingMessage() {
        String message = "  LN-10002 overdue reason?  ";
        when(auditService.begin(anyString(), eq(message), eq(EMPTY_SNAPSHOT), eq(SYSTEM_PROMPT)))
                .thenAnswer(invocation -> new AgentAuditHandle(invocation.getArgument(0), 1L));
        when(gateway.chat(any())).thenReturn("outstanding=3500.00 overdueDays=3");
        when(completionService.complete(any(), eq(EMPTY_SNAPSHOT), eq(message),
                eq("outstanding=3500.00 overdueDays=3"))).thenReturn(25L);

        AgentChatResult result = service.chat(message);

        assertThat(result.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(result.requestId()).matches("[0-9a-f-]{36}");
        InOrder ordered = inOrder(turnStore, auditService, policyRuntimeService, gateway, completionService);
        ordered.verify(turnStore).create();
        ordered.verify(gateway).systemPrompt();
        ordered.verify(auditService).begin(result.requestId(), message, EMPTY_SNAPSHOT, SYSTEM_PROMPT);
        ordered.verify(policyRuntimeService).prepare(result.requestId(), EMPTY_SNAPSHOT, message);
        ordered.verify(gateway).chat(any(AgentChatRequest.class));
        ordered.verify(policyRuntimeService).validateAndRecord(result.answer(), POLICY_PREPARATION);
        ordered.verify(completionService).complete(any(), eq(EMPTY_SNAPSHOT), eq(message), eq(result.answer()));
        verify(metrics).recordRequest("SUCCESS", 25L);
        assertThat(AgentRequestAuditContext.current()).isEmpty();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void blankMessageIsAuditedButNeverCreatesConversationOrCallsPolicyOrModel() {
        String requestId = "11111111-1111-1111-1111-111111111111";
        AgentAuditHandle handle = new AgentAuditHandle(requestId, 1L);
        when(auditService.begin(requestId, "   ")).thenReturn(handle);
        when(auditService.completeFailure(any(), any(InvalidAgentMessageException.class))).thenReturn(4L);

        assertThatThrownBy(() -> service.chatWithRequestId(requestId, null, "   "))
                .isInstanceOf(InvalidAgentMessageException.class);

        verify(turnStore, never()).create();
        verify(policyRuntimeService, never()).prepare(anyString(), any(), anyString());
        verify(gateway, never()).chat(any());
        verify(metrics).recordRequest("FAILED", 4L);
    }

    @Test
    void oversizedMessageIsAuditedBeforeConversationPolicyOrProvider() {
        String requestId = "22222222-2222-2222-2222-222222222222";
        String message = "x".repeat(4001);
        AgentAuditHandle handle = new AgentAuditHandle(requestId, 1L);
        when(auditService.begin(requestId, message)).thenReturn(handle);
        when(auditService.completeFailure(any(), any(InvalidAgentMessageException.class))).thenReturn(5L);

        assertThatThrownBy(() -> service.chatWithRequestId(requestId, null, message))
                .isInstanceOf(InvalidAgentMessageException.class)
                .hasMessage("message must not exceed 4000 characters");

        verify(turnStore, never()).create();
        verify(turnStore, never()).resolve(anyString());
        verify(policyRuntimeService, never()).prepare(anyString(), any(), anyString());
        verify(gateway, never()).chat(any());
        verify(completionService, never()).complete(any(), any(), anyString(), anyString());
        verify(metrics).recordRequest("FAILED", 5L);
    }
    @Test
    void unknownConversationFailsBeforeAuditPolicyOrProvider() {
        when(turnStore.resolve(CONVERSATION_ID)).thenThrow(new ConversationNotFoundException(CONVERSATION_ID));

        assertThatThrownBy(() -> service.chatWithRequestId("request", CONVERSATION_ID, "hello"))
                .isInstanceOf(ConversationNotFoundException.class);

        verify(auditService, never()).begin(anyString(), anyString(), any(), anyString());
        verify(policyRuntimeService, never()).prepare(anyString(), any(), anyString());
        verify(gateway, never()).chat(any());
        verify(metrics).recordRequest(eq("FAILED"), anyLong());
    }

    @Test
    void auditBeginFailurePreventsPolicyAndRemoteModelCall() {
        when(auditService.begin(anyString(), anyString(), eq(EMPTY_SNAPSHOT), eq(SYSTEM_PROMPT)))
                .thenThrow(new IllegalStateException("db unavailable"));

        assertThatThrownBy(() -> service.chat("LN-10002 overdue reason?"))
                .isInstanceOf(IllegalStateException.class).hasMessage("db unavailable");

        verify(policyRuntimeService, never()).prepare(anyString(), any(), anyString());
        verify(gateway, never()).chat(any());
        verify(metrics).recordAuditWriteFailure("agent_begin");
        verify(metrics).recordRequest(eq("AUDIT_FAILED"), anyLong());
    }

    @Test
    void providerFailureIsAuditedAndNeverCompletesConversation() {
        AgentAuditHandle handle = startedAudit("request");
        RuntimeException providerFailure = new RuntimeException("provider failed");
        when(gateway.chat(any())).thenThrow(providerFailure);
        when(auditService.elapsedMillis(handle)).thenReturn(8L);
        when(auditService.completeFailure(handle, providerFailure)).thenReturn(9L);

        assertThatThrownBy(() -> service.chatWithRequestId("request", null, "question"))
                .isSameAs(providerFailure);

        verify(completionService, never()).complete(any(), any(), anyString(), anyString());
        verify(auditService).completeFailure(handle, providerFailure);
        verify(metrics).recordRequest("FAILED", 9L);
    }

    @Test
    void completionConflictIsAuditedAsFailureAndRethrown() {
        AgentAuditHandle handle = startedAudit("request");
        ConversationConflictException conflict = new ConversationConflictException(CONVERSATION_ID, 0, 0);
        when(gateway.chat(any())).thenReturn("answer");
        when(completionService.complete(handle, EMPTY_SNAPSHOT, "question", "answer")).thenThrow(conflict);
        when(auditService.completeFailure(handle, conflict)).thenReturn(12L);

        assertThatThrownBy(() -> service.chatWithRequestId("request", null, "question")).isSameAs(conflict);

        verify(auditService).completeFailure(handle, conflict);
        verify(metrics).recordRequest("FAILED", 12L);
    }

    @Test
    void successfulTurnCommitFailureDoesNotRemainSuccessful() {
        AgentAuditHandle handle = startedAudit("request");
        IllegalStateException persistenceFailure = new IllegalStateException("commit failed");
        when(gateway.chat(any())).thenReturn("answer");
        when(completionService.complete(handle, EMPTY_SNAPSHOT, "question", "answer"))
                .thenThrow(persistenceFailure);
        when(auditService.completeFailure(handle, persistenceFailure)).thenReturn(14L);

        assertThatThrownBy(() -> service.chatWithRequestId("request", null, "question"))
                .isSameAs(persistenceFailure);

        verify(auditService).completeFailure(handle, persistenceFailure);
        verify(metrics).recordAuditWriteFailure("agent_success");
        verify(metrics).recordRequest("FAILED", 14L);
    }

    @Test
    void requestScopedContextAndMdcAreRestored() {
        AgentAuditHandle handle = startedAudit("request");
        when(gateway.chat(any())).thenAnswer(invocation -> {
            assertThat(AgentRequestAuditContext.current()).get()
                    .extracting(AgentRequestAuditContext.State::requestId).isEqualTo("request");
            return "ok";
        });
        when(completionService.complete(handle, EMPTY_SNAPSHOT, "hello", "ok")).thenReturn(1L);
        MDC.put("requestId", "outer-request");

        service.chatWithRequestId("request", null, "hello");

        assertThat(MDC.get("requestId")).isEqualTo("outer-request");
        assertThat(AgentRequestAuditContext.current()).isEmpty();
    }



    @Test
    void normalStreamEmitsOrderedDeltasThenCommitsAggregatedAnswer() {
        AgentAuditHandle handle = startedAudit("request");
        when(gateway.stream(any())).thenReturn(reactor.core.publisher.Flux.just("当前", "应还", "8500元"));
        when(completionService.complete(handle, EMPTY_SNAPSHOT, "question", "当前应还8500元"))
                .thenReturn(25L);

        List<AgentStreamEvent> events = service
                .streamWithRequestId("request", null, "question")
                .collectList().block();

        assertThat(events).extracting(AgentStreamEvent::event)
                .containsExactly("start", "delta", "delta", "delta", "done");
        assertThat(events.subList(1, 4))
                .extracting(event -> ((AgentStreamEvent.DeltaData) event.data()).text())
                .containsExactly("当前", "应还", "8500元");
        verify(policyRuntimeService).validateAndRecord("当前应还8500元", POLICY_PREPARATION);
        verify(completionService).complete(handle, EMPTY_SNAPSHOT, "question", "当前应还8500元");
        verify(metrics).recordRequest("SUCCESS", 25L);
    }

    @Test
    void asynchronousProviderFailureEmitsErrorAndFailsAuditWithoutCommit() {
        AgentAuditHandle handle = startedAudit("request");
        com.loanops.exception.AgentProviderUnavailableException failure =
                new com.loanops.exception.AgentProviderUnavailableException(
                        new RuntimeException("provider failed"));
        when(gateway.stream(any())).thenReturn(reactor.core.publisher.Flux.error(failure));
        when(auditService.completeFailure(handle, failure)).thenReturn(9L);

        List<AgentStreamEvent> events = service
                .streamWithRequestId("request", null, "question")
                .collectList().block();

        assertThat(events).extracting(AgentStreamEvent::event)
                .containsExactly("start", "error");
        AgentStreamEvent.ErrorData error = (AgentStreamEvent.ErrorData) events.getLast().data();
        assertThat(error.code()).isEqualTo("AGENT_PROVIDER_UNAVAILABLE");
        assertThat(error.committed()).isFalse();
        verify(auditService).completeFailure(handle, failure);
        verify(completionService, never()).complete(any(), any(), anyString(), anyString());
        verify(metrics).recordRequest("FAILED", 9L);
    }

    @Test
    void conversationConflictAfterProvisionalDeltaEmitsErrorWithoutDone() {
        AgentAuditHandle handle = startedAudit("request");
        ConversationConflictException conflict =
                new ConversationConflictException(CONVERSATION_ID, 0, 0);
        when(gateway.stream(any())).thenReturn(reactor.core.publisher.Flux.just("provisional"));
        when(completionService.complete(handle, EMPTY_SNAPSHOT, "question", "provisional"))
                .thenThrow(conflict);
        when(auditService.completeFailure(handle, conflict)).thenReturn(12L);

        List<AgentStreamEvent> events = service
                .streamWithRequestId("request", null, "question")
                .collectList().block();

        assertThat(events).extracting(AgentStreamEvent::event)
                .containsExactly("start", "delta", "error");
        assertThat(((AgentStreamEvent.ErrorData) events.getLast().data()).code())
                .isEqualTo("CONVERSATION_CONFLICT");
        verify(auditService).completeFailure(handle, conflict);
    }

    @Test
    void cancellationFailsAuditAndNeverCommitsTurn() {
        AgentAuditHandle handle = startedAudit("request");
        when(gateway.stream(any())).thenReturn(reactor.core.publisher.Flux.never());
        when(auditService.completeFailure(eq(handle), any(java.util.concurrent.CancellationException.class)))
                .thenReturn(7L);
        java.util.List<AgentStreamEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        reactor.core.Disposable subscription = service
                .streamWithRequestId("request", null, "question")
                .subscribe(events::add);
        subscription.dispose();

        assertThat(events).extracting(AgentStreamEvent::event).containsExactly("start");
        verify(auditService).completeFailure(
                eq(handle), any(java.util.concurrent.CancellationException.class));
        verify(completionService, never()).complete(any(), any(), anyString(), anyString());
        verify(metrics).recordRequest("FAILED", 7L);
    }

    @Test
    void bufferedPolicyDoesNotExposeAnswerUntilValidationAndCommitSucceed() {
        PolicyRuntimePreparation preparation = preparation(
                PolicyRetrievalDecision.SUPPLEMENTAL, PolicyRetrievalStatus.MATCHED);
        when(policyRuntimeService.prepare("request", EMPTY_SNAPSHOT, "policy question"))
                .thenReturn(preparation);
        when(policyRuntimeService.validateAndRecord("policy [P1]", preparation))
                .thenReturn("policy [P1]");
        AgentAuditHandle handle = startedAudit("request");
        when(gateway.stream(any())).thenReturn(reactor.core.publisher.Flux.just("policy ", "[P1]"));
        when(completionService.complete(handle, EMPTY_SNAPSHOT, "policy question", "policy [P1]"))
                .thenReturn(30L);

        List<AgentStreamEvent> events = service
                .streamWithRequestId("request", null, "policy question")
                .collectList().block();

        assertThat(events).extracting(AgentStreamEvent::event)
                .containsExactly("start", "delta", "done");
        assertThat(((AgentStreamEvent.StartData) events.getFirst().data()).deliveryMode())
                .isEqualTo("BUFFERED_POLICY");
        assertThat(((AgentStreamEvent.DeltaData) events.get(1).data()).text())
                .isEqualTo("policy [P1]");
        InOrder ordered = inOrder(policyRuntimeService, completionService);
        ordered.verify(policyRuntimeService).validateAndRecord("policy [P1]", preparation);
        ordered.verify(completionService).complete(
                handle, EMPTY_SNAPSHOT, "policy question", "policy [P1]");
    }

    @Test
    void policyCitationFailureEmitsNoAnswerDeltaAndNoDone() {
        PolicyRuntimePreparation preparation = preparation(
                PolicyRetrievalDecision.REQUIRED, PolicyRetrievalStatus.MATCHED);
        when(policyRuntimeService.prepare("request", EMPTY_SNAPSHOT, "policy question"))
                .thenReturn(preparation);
        com.loanops.exception.PolicyCitationValidationException failure =
                new com.loanops.exception.PolicyCitationValidationException("missing citation");
        when(policyRuntimeService.validateAndRecord("unsafe draft", preparation)).thenThrow(failure);
        AgentAuditHandle handle = startedAudit("request");
        when(gateway.stream(any())).thenReturn(reactor.core.publisher.Flux.just("unsafe ", "draft"));
        when(auditService.completeFailure(handle, failure)).thenReturn(11L);

        List<AgentStreamEvent> events = service
                .streamWithRequestId("request", null, "policy question")
                .collectList().block();

        assertThat(events).extracting(AgentStreamEvent::event)
                .containsExactly("start", "error");
        assertThat(((AgentStreamEvent.ErrorData) events.getLast().data()).code())
                .isEqualTo("POLICY_CITATION_VALIDATION_FAILED");
        verify(completionService, never()).complete(any(), any(), anyString(), anyString());
        verify(auditService).completeFailure(handle, failure);
    }

    @Test
    void requiredNoMatchSkipsModelAndCommitsDeterministicNoticeBeforeDelta() {
        PolicyRuntimePreparation preparation = preparation(
                PolicyRetrievalDecision.REQUIRED, PolicyRetrievalStatus.NO_MATCH);
        when(policyRuntimeService.prepare("request", EMPTY_SNAPSHOT, "policy question"))
                .thenReturn(preparation);
        when(policyRuntimeService.validateAndRecord(
                com.loanops.policy.PolicyGroundingContextFactory.NO_MATCH_NOTICE, preparation))
                .thenReturn(com.loanops.policy.PolicyGroundingContextFactory.NO_MATCH_NOTICE);
        AgentAuditHandle handle = startedAudit("request");
        when(completionService.complete(
                handle, EMPTY_SNAPSHOT, "policy question",
                com.loanops.policy.PolicyGroundingContextFactory.NO_MATCH_NOTICE)).thenReturn(15L);

        List<AgentStreamEvent> events = service
                .streamWithRequestId("request", null, "policy question")
                .collectList().block();

        assertThat(events).extracting(AgentStreamEvent::event)
                .containsExactly("start", "delta", "done");
        verify(gateway, never()).stream(any());
    }

    private PolicyRuntimePreparation preparation(
            PolicyRetrievalDecision decision, PolicyRetrievalStatus status) {
        return new PolicyRuntimePreparation(
                new PolicyGroundingContext(decision, status, LocalDate.of(2026, 1, 1),
                        "hash", List.of(), ""),
                new PolicyRetrievalAuditHandle("retrieval", 1L));
    }

    private AgentAuditHandle startedAudit(String requestId) {
        AgentAuditHandle handle = new AgentAuditHandle(requestId, 1L);
        when(auditService.begin(eq(requestId), anyString(), eq(EMPTY_SNAPSHOT), eq(SYSTEM_PROMPT)))
                .thenReturn(handle);
        return handle;
    }
}
