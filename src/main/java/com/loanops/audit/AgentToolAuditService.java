package com.loanops.audit;

import com.loanops.observability.AgentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class AgentToolAuditService {

    private static final Logger log = LoggerFactory.getLogger(AgentToolAuditService.class);

    private final AgentAuditService auditService;
    private final AgentMetrics metrics;

    public AgentToolAuditService(AgentAuditService auditService, AgentMetrics metrics) {
        this.auditService = auditService;
        this.metrics = metrics;
    }

    public <T> T execute(String toolName, String loanNo, Supplier<T> operation) {
        AgentRequestAuditContext.State context = AgentRequestAuditContext.current().orElse(null);
        if (context == null) {
            return operation.get();
        }

        int sequenceNo = context.nextSequence();
        ToolAuditHandle handle;
        try {
            // Fail closed inside an Agent request: do not execute a tool if its STARTED row cannot be written.
            handle = auditService.beginTool(context.requestId(), sequenceNo, toolName, loanNo);
        } catch (RuntimeException auditFailure) {
            metrics.recordAuditWriteFailure("tool_begin");
            log.error("Tool audit begin failed requestId={} sequenceNo={} tool={} errorType={}",
                    context.requestId(), sequenceNo, toolName, auditFailure.getClass().getSimpleName());
            throw auditFailure;
        }

        T result;
        try {
            result = operation.get();
        } catch (RuntimeException | Error operationFailure) {
            long durationMs = auditService.elapsedMillis(handle);
            try {
                durationMs = auditService.completeToolFailure(handle, operationFailure);
            } catch (RuntimeException auditFailure) {
                metrics.recordAuditWriteFailure("tool_failure");
                operationFailure.addSuppressed(auditFailure);
                log.error("Tool failure audit completion failed requestId={} sequenceNo={} tool={} errorType={}",
                        context.requestId(), sequenceNo, toolName, auditFailure.getClass().getSimpleName());
            }
            metrics.recordTool(toolName, "FAILED", durationMs);
            log.info("Agent tool completed requestId={} sequenceNo={} tool={} outcome=FAILED durationMs={} errorType={}",
                    context.requestId(), sequenceNo, toolName, durationMs,
                    operationFailure.getClass().getSimpleName());
            throw operationFailure;
        }

        long durationMs;
        try {
            durationMs = auditService.completeToolSuccess(handle);
        } catch (RuntimeException auditFailure) {
            metrics.recordAuditWriteFailure("tool_success");
            log.error("Tool success audit completion failed requestId={} sequenceNo={} tool={} errorType={}",
                    context.requestId(), sequenceNo, toolName, auditFailure.getClass().getSimpleName());
            throw auditFailure;
        }
        metrics.recordTool(toolName, "SUCCESS", durationMs);
        log.info("Agent tool completed requestId={} sequenceNo={} tool={} outcome=SUCCESS durationMs={}",
                context.requestId(), sequenceNo, toolName, durationMs);
        return result;
    }
}