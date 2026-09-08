package com.loanops.controller;

import com.loanops.agent.AgentChatGateway;
import com.loanops.audit.AgentAuditService;
import com.loanops.dto.AgentAuditResponse;
import com.loanops.policy.PolicyRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.ai.deepseek.api-key=test-key",
        "loanops.business-date=2026-08-23",
        "loanops.agent.max-message-characters=8"
})
@ActiveProfiles("ai")
@AutoConfigureMockMvc
class AgentValidationAuditIntegrationTest {

    @MockitoBean
    private AgentChatGateway gateway;

    @MockitoBean
    private PolicyRetriever policyRetriever;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentAuditService auditService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearAuditRows() {
        jdbcTemplate.update("DELETE FROM agent_tool_audit_log");
        jdbcTemplate.update("DELETE FROM conversation_message");
        jdbcTemplate.update("DELETE FROM agent_audit_log");
    }

    @Test
    void blankHttpRequestGetsCorrelationIdAndPersistsFailedAuditWithoutCallingProvider() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("INVALID_AGENT_MESSAGE"))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).matches("[0-9a-f-]{36}");

        AgentAuditResponse audit = auditService.get(requestId);
        assertThat(audit.status()).isEqualTo("FAILED");
        assertThat(audit.errorType()).isEqualTo("InvalidAgentMessageException");
        assertThat(audit.messageLength()).isEqualTo(3);
        assertThat(audit.messageText()).isNull();
        assertThat(audit.tools()).isEmpty();
    }
    @Test
    void oversizedMessageReturns400AndFailsAuditWithoutProviderRetrieverOrTranscript() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"123456789\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("INVALID_AGENT_MESSAGE"))
                .andExpect(jsonPath("$.message").value("message must not exceed 8 characters"))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        AgentAuditResponse audit = auditService.get(requestId);
        assertThat(audit.status()).isEqualTo("FAILED");
        assertThat(audit.errorType()).isEqualTo("InvalidAgentMessageException");
        assertThat(audit.messageLength()).isEqualTo(9);
        assertThat(audit.tools()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM conversation_message", Long.class)).isZero();
        verifyNoInteractions(gateway, policyRetriever);
    }
    @Test
    void malformedJsonStillGetsTransportCorrelationIdButDoesNotCreateAgentAudit() throws Exception {
        long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_audit_log", Long.class);

        MvcResult result = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Request-Id")).matches("[0-9a-f-]{36}");
        long after = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_audit_log", Long.class);
        assertThat(after).isEqualTo(before);
    }

}
