package com.loanops.audit;

import com.loanops.dto.AgentAuditResponse;
import com.loanops.exception.LoanNotFoundException;
import com.loanops.tool.LoanOpsTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "loanops.business-date=2026-08-23")
class AgentToolAuditIntegrationTest {

    @Autowired
    private AgentAuditService auditService;

    @Autowired
    private LoanOpsTools tools;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearAuditRows() {
        jdbcTemplate.update("DELETE FROM agent_tool_audit_log");
        jdbcTemplate.update("DELETE FROM agent_audit_log");
    }

    @Test
    void requestContextCorrelatesToolCallsAndPreservesSequence() {
        String requestId = UUID.randomUUID().toString();
        AgentAuditHandle handle = auditService.begin(requestId, "check three facts");

        try (AgentRequestAuditContext.Scope ignored = AgentRequestAuditContext.open(requestId)) {
            tools.getCurrentRepayment("LN-10001");
            tools.getOverdueDiagnosis("LN-10002");
            tools.getSettlementStatus("LN-10003");
        }
        auditService.completeSuccess(handle, "done");

        AgentAuditResponse audit = auditService.get(requestId);
        assertThat(audit.tools()).hasSize(3);
        assertThat(audit.tools()).extracting(tool -> tool.sequenceNo()).containsExactly(1, 2, 3);
        assertThat(audit.tools()).extracting(tool -> tool.toolName()).containsExactly(
                "getCurrentRepayment", "getOverdueDiagnosis", "getSettlementStatus");
        assertThat(audit.tools()).extracting(tool -> tool.loanNo()).containsExactly(
                "LN-10001", "LN-10002", "LN-10003");
        assertThat(audit.tools()).extracting(tool -> tool.status()).containsOnly("SUCCESS");
        assertThat(AgentRequestAuditContext.current()).isEmpty();
    }

    @Test
    void toolFailureIsAuditedAndOriginalBusinessExceptionIsPreserved() {
        String requestId = UUID.randomUUID().toString();
        auditService.begin(requestId, "unknown loan");

        try (AgentRequestAuditContext.Scope ignored = AgentRequestAuditContext.open(requestId)) {
            assertThatThrownBy(() -> tools.getOverdueDiagnosis("LN-NOT-FOUND"))
                    .isInstanceOf(LoanNotFoundException.class);
        }

        AgentAuditResponse audit = auditService.get(requestId);
        assertThat(audit.tools()).hasSize(1);
        assertThat(audit.tools().getFirst().status()).isEqualTo("FAILED");
        assertThat(audit.tools().getFirst().errorType()).isEqualTo("LoanNotFoundException");
    }

    @Test
    void directToolCallWithoutAgentContextDoesNotCreateToolAudit() {
        long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_tool_audit_log", Long.class);

        tools.getCurrentRepayment("LN-10001");

        long after = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_tool_audit_log", Long.class);
        assertThat(after).isEqualTo(before);
    }
}