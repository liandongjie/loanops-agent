package com.loanops.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureObservability
@AutoConfigureMockMvc
class AgentObservabilityIntegrationTest {

    private static final Set<String> FORBIDDEN_HIGH_CARDINALITY_TAGS = Set.of(
            "requestId", "request_id", "loanNo", "loan_no", "message", "prompt");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentMetrics metrics;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void onlyRequiredActuatorEndpointsAreExposedAndPrometheusContainsLoanOpsMetrics() throws Exception {
        metrics.recordRequest("SUCCESS", 12L);
        metrics.recordTool("getCurrentRepayment", "SUCCESS", 3L);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());

        String prometheus = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(prometheus).contains("loanops_agent_requests_total");
        assertThat(prometheus).contains("loanops_agent_tool_executions_total");

        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/configprops"))
                .andExpect(status().isNotFound());
    }

    @Test
    void customMetricsNeverUseRequestOrLoanIdentifiersAsTags() {
        metrics.recordRequest("FAILED", 1L);
        metrics.recordTool("getOverdueDiagnosis", "FAILED", 2L);
        metrics.recordAuditWriteFailure("agent_failure");

        assertThat(meterRegistry.getMeters())
                .filteredOn(meter -> meter.getId().getName().startsWith("loanops.agent"))
                .allSatisfy(this::assertLowCardinalityTags);
    }

    private void assertLowCardinalityTags(Meter meter) {
        assertThat(meter.getId().getTags())
                .noneSatisfy(tag -> assertThat(FORBIDDEN_HIGH_CARDINALITY_TAGS).contains(tag.getKey()));
    }
}
