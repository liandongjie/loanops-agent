package com.loanops.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayMigrationIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void requiredBaselineMigrationsAreAppliedWithoutBlockingFutureVersions() {
        assertThat(flyway.info().current()).isNotNull();

        assertThat(Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .filter(version -> version != null)
                .map(Object::toString)
                .toList())
                .contains("1", "2", "3", "4");
    }

    @Test
    void requiredDemoFixturesAreLoadedByFlyway() {
        Integer loanCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM loan_contract WHERE loan_no IN ('LN-10001', 'LN-10002', 'LN-10003')",
                Integer.class);
        Integer planCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM repayment_plan WHERE id IN (11, 21, 31, 32)",
                Integer.class);
        Integer paymentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_record WHERE id IN (201, 301, 302)",
                Integer.class);

        assertThat(loanCount).isEqualTo(3);
        assertThat(planCount).isEqualTo(4);
        assertThat(paymentCount).isEqualTo(3);
    }

    @Test
    void v3CreatesBothAuditTables() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT request_id FROM agent_audit_log WHERE 1 = 0")).isEmpty();
        assertThat(jdbcTemplate.queryForList(
                "SELECT request_id FROM agent_tool_audit_log WHERE 1 = 0")).isEmpty();
    }

    @Test
    void v4CreatesConversationTablesAndExtendsAgentAudit() {
        assertThat(jdbcTemplate.queryForList("""
                SELECT conversation_id, created_at, updated_at, version, last_message_sequence
                FROM conversation
                WHERE 1 = 0
                """)).isEmpty();
        assertThat(jdbcTemplate.queryForList("""
                SELECT message_id, conversation_id, request_id, sequence_no, role, content, created_at
                FROM conversation_message
                WHERE 1 = 0
                """)).isEmpty();
        assertThat(jdbcTemplate.queryForList("""
                SELECT conversation_id, history_from_sequence, history_to_sequence,
                       history_hash, system_prompt_hash
                FROM agent_audit_log
                WHERE 1 = 0
                """)).isEmpty();
    }
}
