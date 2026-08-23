package com.loanops.agent;

import com.loanops.audit.AgentAuditHandle;
import com.loanops.audit.AgentAuditService;
import com.loanops.audit.AgentRequestAuditContext;
import com.loanops.dto.AgentChatResult;
import com.loanops.exception.InvalidAgentMessageException;
import com.loanops.observability.AgentMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.MDC;

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

    private final AgentChatGateway gateway = mock(AgentChatGateway.class);
    private final AgentAuditService auditService = mock(AgentAuditService.class);
    private final AgentMetrics metrics = mock(AgentMetrics.class);
    private final LoanOpsAgentService service = new LoanOpsAgentService(
            gateway, auditService, metrics, "deepseek", "deepseek-chat");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void auditStartCommitsBeforeRemoteModelCallAndMessageIsNotSilentlyTrimmed() {
        String message = "  LN-10002 overdue reason?  ";
        AgentAuditHandle handle = new AgentAuditHandle("ignored", 1L);
        when(auditService.begin(anyString(), anyString())).thenReturn(handle);
        when(gateway.chat(message)).thenReturn("outstanding=3500.00 overdueDays=3");
        when(auditService.completeSuccess(handle, "outstanding=3500.00 overdueDays=3")).thenReturn(25L);

        AgentChatResult result = service.chat(message);

        assertThat(result.requestId()).matches("[0-9a-f-]{36}");
        InOrder ordered = inOrder(auditService, gateway);
        ordered.verify(auditService).begin(result.requestId(), message);
        ordered.verify(gateway).chat(message);
        ordered.verify(auditService).completeSuccess(handle, result.answer());
        verify(metrics).recordRequest("SUCCESS", 25L);
        assertThat(AgentRequestAuditContext.current()).isEmpty();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void blankMessageIsAuditedAsFailedAndNeverCallsRemoteModel() {
        String requestId = "11111111-1111-1111-1111-111111111111";
        AgentAuditHandle handle = new AgentAuditHandle(requestId, 1L);
        when(auditService.begin(requestId, "   ")).thenReturn(handle);
        when(auditService.completeFailure(any(), any(InvalidAgentMessageException.class))).thenReturn(4L);

        assertThatThrownBy(() -> service.chatWithRequestId(requestId, "   "))
                .isInstanceOf(InvalidAgentMessageException.class);

        verify(auditService).begin(requestId, "   ");
        verify(auditService).completeFailure(any(), any(InvalidAgentMessageException.class));
        verify(gateway, never()).chat(anyString());
        verify(metrics).recordRequest("FAILED", 4L);
        assertThat(AgentRequestAuditContext.current()).isEmpty();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void blankMessageStillRecordsFailedRequestMetricWhenFailureAuditFinalizationBreaks() {
        String requestId = "33333333-3333-3333-3333-333333333333";
        AgentAuditHandle handle = new AgentAuditHandle(requestId, 1L);
        when(auditService.begin(requestId, "   ")).thenReturn(handle);
        when(auditService.elapsedMillis(handle)).thenReturn(6L);
        when(auditService.completeFailure(any(), any(InvalidAgentMessageException.class)))
                .thenThrow(new IllegalStateException("audit update failed"));

        assertThatThrownBy(() -> service.chatWithRequestId(requestId, "   "))
                .isInstanceOf(InvalidAgentMessageException.class)
                .satisfies(failure -> assertThat(failure.getSuppressed())
                        .singleElement()
                        .isInstanceOf(IllegalStateException.class));

        verify(gateway, never()).chat(anyString());
        verify(metrics).recordAuditWriteFailure("agent_failure");
        verify(metrics).recordRequest("FAILED", 6L);
    }

    @Test
    void auditBeginFailurePreventsRemoteModelCall() {
        when(auditService.begin(anyString(), anyString())).thenThrow(new IllegalStateException("db unavailable"));

        assertThatThrownBy(() -> service.chat("LN-10002 overdue reason?"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db unavailable");

        verify(gateway, never()).chat(anyString());
        verify(metrics).recordAuditWriteFailure("agent_begin");
        verify(metrics).recordRequest(eq("AUDIT_FAILED"), anyLong());
        assertThat(AgentRequestAuditContext.current()).isEmpty();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void modelFailureIsPersistedAsTechnicalFailureAndRethrown() {
        AgentAuditHandle handle = new AgentAuditHandle("ignored", 1L);
        RuntimeException modelFailure = new RuntimeException("provider failed");
        when(auditService.begin(anyString(), anyString())).thenReturn(handle);
        when(gateway.chat(anyString())).thenThrow(modelFailure);
        when(auditService.elapsedMillis(handle)).thenReturn(8L);
        when(auditService.completeFailure(handle, modelFailure)).thenReturn(9L);

        assertThatThrownBy(() -> service.chat("LN-10002 overdue reason?"))
                .isSameAs(modelFailure);

        verify(auditService).completeFailure(handle, modelFailure);
        verify(metrics).recordRequest("FAILED", 9L);
        assertThat(AgentRequestAuditContext.current()).isEmpty();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void successAuditCompletionFailureDoesNotRewriteRequestAsModelFailure() {
        AgentAuditHandle handle = new AgentAuditHandle("ignored", 1L);
        when(auditService.begin(anyString(), anyString())).thenReturn(handle);
        when(gateway.chat(anyString())).thenReturn("ok");
        when(auditService.completeSuccess(handle, "ok")).thenThrow(new IllegalStateException("audit update failed"));
        when(auditService.elapsedMillis(handle)).thenReturn(11L);

        assertThatThrownBy(() -> service.chat("LN-10001 current repayment?"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit update failed");

        verify(auditService, never()).completeFailure(any(), any());
        verify(metrics).recordAuditWriteFailure("agent_success");
        verify(metrics).recordRequest("AUDIT_FAILED", 11L);
    }

    @Test
    void requestScopedMdcRestoresPreviousValueInsteadOfLeakingOrErasingOuterContext() {
        String requestId = "22222222-2222-2222-2222-222222222222";
        AgentAuditHandle handle = new AgentAuditHandle(requestId, 1L);
        when(auditService.begin(requestId, "hello")).thenReturn(handle);
        when(gateway.chat("hello")).thenReturn("ok");
        when(auditService.completeSuccess(handle, "ok")).thenReturn(1L);
        MDC.put("requestId", "outer-request");

        service.chatWithRequestId(requestId, "hello");

        assertThat(MDC.get("requestId")).isEqualTo("outer-request");
        assertThat(AgentRequestAuditContext.current()).isEmpty();
    }
}
