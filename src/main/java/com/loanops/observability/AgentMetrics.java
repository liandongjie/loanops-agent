package com.loanops.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
public class AgentMetrics {

    private static final Set<String> REQUEST_OUTCOMES = Set.of("SUCCESS", "FAILED", "AUDIT_FAILED");
    private static final Set<String> TOOL_OUTCOMES = Set.of("SUCCESS", "FAILED");
    private static final Set<String> TOOL_NAMES = Set.of(
            "getCurrentRepayment", "getOverdueDiagnosis", "getSettlementStatus");
    private static final Set<String> AUDIT_OPERATIONS = Set.of(
            "agent_begin", "agent_success", "agent_failure", "tool_begin", "tool_success", "tool_failure");

    private final MeterRegistry meterRegistry;

    public AgentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest(String outcome, long durationMs) {
        String safeOutcome = bounded(outcome, REQUEST_OUTCOMES);
        meterRegistry.counter("loanops.agent.requests", "outcome", safeOutcome).increment();
        Timer.builder("loanops.agent.request.duration")
                .tag("outcome", safeOutcome)
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0L, durationMs)));
    }

    public void recordTool(String toolName, String outcome, long durationMs) {
        String safeToolName = bounded(toolName, TOOL_NAMES);
        String safeOutcome = bounded(outcome, TOOL_OUTCOMES);
        meterRegistry.counter("loanops.agent.tool.executions", "tool", safeToolName, "outcome", safeOutcome).increment();
        Timer.builder("loanops.agent.tool.duration")
                .tag("tool", safeToolName)
                .tag("outcome", safeOutcome)
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0L, durationMs)));
    }

    public void recordAuditWriteFailure(String operation) {
        meterRegistry.counter("loanops.agent.audit.write.failures", "operation", bounded(operation, AUDIT_OPERATIONS))
                .increment();
    }

    private static String bounded(String value, Set<String> allowed) {
        return allowed.contains(value) ? value : "unknown";
    }
}