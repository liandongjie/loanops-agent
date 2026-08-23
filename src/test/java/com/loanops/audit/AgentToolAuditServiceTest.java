package com.loanops.audit;

import com.loanops.observability.AgentMetrics;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolAuditServiceTest {

    private final AgentAuditService auditService = mock(AgentAuditService.class);
    private final AgentMetrics metrics = mock(AgentMetrics.class);
    private final AgentToolAuditService service = new AgentToolAuditService(auditService, metrics);

    @Test
    void failedToolKeepsRealDurationWhenFailureAuditFinalizationAlsoFails() {
        String requestId = "33333333-3333-3333-3333-333333333333";
        ToolAuditHandle handle = new ToolAuditHandle(requestId, 1, 10L);
        RuntimeException toolFailure = new RuntimeException("tool failed");
        RuntimeException auditFailure = new RuntimeException("audit update failed");
        when(auditService.beginTool(requestId, 1, "getCurrentRepayment", "LN-10001")).thenReturn(handle);
        when(auditService.elapsedMillis(handle)).thenReturn(7L);
        when(auditService.completeToolFailure(handle, toolFailure)).thenThrow(auditFailure);

        try (AgentRequestAuditContext.Scope ignored = AgentRequestAuditContext.open(requestId)) {
            assertThatThrownBy(() -> service.execute("getCurrentRepayment", "LN-10001", () -> {
                throw toolFailure;
            })).isSameAs(toolFailure);
        }

        verify(metrics).recordAuditWriteFailure("tool_failure");
        verify(metrics).recordTool("getCurrentRepayment", "FAILED", 7L);
        verify(auditService).completeToolFailure(handle, toolFailure);
    }
}
