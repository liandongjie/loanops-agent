package com.loanops.controller;

import com.loanops.audit.AgentAuditHandle;
import com.loanops.audit.AgentAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "loanops.business-date=2026-08-23")
@AutoConfigureMockMvc
class AgentAuditControllerIntegrationTest {

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
    void getReturnsAuditableMetadataWithoutRawContentByDefault() throws Exception {
        String requestId = UUID.randomUUID().toString();
        AgentAuditHandle handle = auditService.begin(requestId, "LN-10002 为什么逾期？");
        auditService.completeSuccess(handle, "逾期 3 天。");

        mockMvc.perform(get("/api/agent/audits/{requestId}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.messageHash").isString())
                .andExpect(jsonPath("$.messageText").isEmpty())
                .andExpect(jsonPath("$.answerText").isEmpty())
                .andExpect(jsonPath("$.tools").isArray());
    }

    @Test
    void missingAuditReturnsStable404Code() throws Exception {
        mockMvc.perform(get("/api/agent/audits/{requestId}", "missing-request"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AGENT_AUDIT_NOT_FOUND"));
    }
}