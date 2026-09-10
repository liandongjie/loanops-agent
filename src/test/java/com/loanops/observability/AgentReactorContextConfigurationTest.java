package com.loanops.observability;

import com.loanops.audit.AgentAuditService;
import com.loanops.audit.AgentRequestAuditContext;
import com.loanops.audit.AgentToolAuditService;
import com.loanops.audit.ToolAuditHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentReactorContextConfigurationTest {

    @AfterEach
    void clearThreadLocals() {
        AgentRequestAuditContext.clear();
        MDC.clear();
    }

    @Test
    void propagatesAuditStateSequenceAndMdcAcrossBoundedElasticThreadHop() {
        new AgentReactorContextConfiguration().configure();
        AgentAuditService auditService = mock(AgentAuditService.class);
        AgentMetrics metrics = mock(AgentMetrics.class);
        AgentToolAuditService toolAuditService = new AgentToolAuditService(auditService, metrics);
        ToolAuditHandle first = new ToolAuditHandle("request-123", 1, 1L);
        ToolAuditHandle second = new ToolAuditHandle("request-123", 2, 2L);
        when(auditService.beginTool("request-123", 1, "firstTool", "LN-10002")).thenReturn(first);
        when(auditService.beginTool("request-123", 2, "secondTool", "LN-10002")).thenReturn(second);
        when(auditService.completeToolSuccess(first)).thenReturn(3L);
        when(auditService.completeToolSuccess(second)).thenReturn(4L);
        AtomicReference<String> workerMdc = new AtomicReference<>();
        AtomicReference<String> workerThread = new AtomicReference<>();

        AgentRequestAuditContext.State state;
        try (AgentRequestAuditContext.Scope ignored = AgentRequestAuditContext.open("request-123")) {
            state = AgentRequestAuditContext.current().orElseThrow();
        }

        Flux.just("firstTool", "secondTool")
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(tool -> {
                    workerThread.set(Thread.currentThread().getName());
                    workerMdc.set(MDC.get("requestId"));
                    toolAuditService.execute(tool, "LN-10002", () -> "ok");
                })
                .contextWrite(context -> context
                        .put(AgentReactorContextConfiguration.AUDIT_CONTEXT_KEY, state)
                        .put(AgentReactorContextConfiguration.REQUEST_ID_CONTEXT_KEY, "request-123"))
                .blockLast();

        assertThat(workerThread.get()).contains("boundedElastic");
        assertThat(workerMdc.get()).isEqualTo("request-123");
        verify(auditService).beginTool("request-123", 1, "firstTool", "LN-10002");
        verify(auditService).beginTool("request-123", 2, "secondTool", "LN-10002");
        verify(auditService).completeToolSuccess(first);
        verify(auditService).completeToolSuccess(second);
    }
}
