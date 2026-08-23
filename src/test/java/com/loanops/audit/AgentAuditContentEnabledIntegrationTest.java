package com.loanops.audit;

import com.loanops.dto.AgentAuditResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "loanops.audit.include-content=true",
        "loanops.business-date=2026-08-23"
})
class AgentAuditContentEnabledIntegrationTest {

    @Autowired
    private AgentAuditService auditService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearAuditRows() {
        jdbcTemplate.update("DELETE FROM agent_tool_audit_log");
        jdbcTemplate.update("DELETE FROM agent_audit_log");
    }

    @Test
    void explicitOptInPersistsPromptAndAnswerText() {
        String requestId = UUID.randomUUID().toString();
        String message = "LN-10001 当前应还多少？";
        String answer = "8500.00 元。";

        AgentAuditHandle handle = auditService.begin(requestId, message);
        auditService.completeSuccess(handle, answer);

        AgentAuditResponse audit = auditService.get(requestId);
        assertThat(audit.messageText()).isEqualTo(message);
        assertThat(audit.answerText()).isEqualTo(answer);
        assertThat(audit.messageHash()).hasSize(64);
        assertThat(audit.answerHash()).hasSize(64);
    }
}