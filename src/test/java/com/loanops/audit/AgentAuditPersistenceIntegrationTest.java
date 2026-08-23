package com.loanops.audit;

import com.loanops.dto.AgentAuditResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "loanops.business-date=2026-08-23")
class AgentAuditPersistenceIntegrationTest {

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
    void beginThenSuccessPersistsMetadataAndFingerprintsWithoutRawContentByDefault() {
        String requestId = UUID.randomUUID().toString();
        String message = "LN-10002 为什么逾期？";
        String answer = "未偿还 3500.00 元，逾期 3 天。";

        AgentAuditHandle handle = auditService.begin(requestId, message);
        AgentAuditResponse started = auditService.get(requestId);
        assertThat(started.status()).isEqualTo("STARTED");
        assertThat(started.businessDate()).isEqualTo(LocalDate.of(2026, 8, 23));
        assertThat(started.messageLength()).isEqualTo(message.length());
        assertThat(started.messageHash()).hasSize(64);
        assertThat(started.messageText()).isNull();
        assertThat(started.answerText()).isNull();

        auditService.completeSuccess(handle, answer);
        AgentAuditResponse completed = auditService.get(requestId);
        assertThat(completed.status()).isEqualTo("SUCCESS");
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.durationMs()).isNotNegative();
        assertThat(completed.answerLength()).isEqualTo(answer.length());
        assertThat(completed.answerHash()).hasSize(64);
        assertThat(completed.messageText()).isNull();
        assertThat(completed.answerText()).isNull();
        assertThat(completed.errorType()).isNull();
    }

    @Test
    void beginThenFailurePersistsOnlyTechnicalErrorType() {
        String requestId = UUID.randomUUID().toString();
        AgentAuditHandle handle = auditService.begin(requestId, "LN-10002 为什么逾期？");

        auditService.completeFailure(handle, new IllegalStateException("provider body must not be persisted"));

        AgentAuditResponse completed = auditService.get(requestId);
        assertThat(completed.status()).isEqualTo("FAILED");
        assertThat(completed.errorType()).isEqualTo("IllegalStateException");
        assertThat(completed.answerHash()).isNull();
        assertThat(completed.answerText()).isNull();
    }
}