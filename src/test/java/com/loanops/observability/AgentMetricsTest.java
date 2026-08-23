package com.loanops.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMetricsTest {

    @Test
    void arbitraryValuesCollapseToUnknownInsteadOfCreatingHighCardinalitySeries() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMetrics metrics = new AgentMetrics(registry);

        metrics.recordRequest("request-123", 1L);
        metrics.recordTool("LN-10001", "request-456", 2L);
        metrics.recordAuditWriteFailure("loan-789");

        assertThat(registry.find("loanops.agent.requests").tag("outcome", "unknown").counter()).isNotNull();
        assertThat(registry.find("loanops.agent.tool.executions").tag("tool", "unknown").tag("outcome", "unknown").counter()).isNotNull();
        assertThat(registry.find("loanops.agent.audit.write.failures").tag("operation", "unknown").counter()).isNotNull();
    }
}
