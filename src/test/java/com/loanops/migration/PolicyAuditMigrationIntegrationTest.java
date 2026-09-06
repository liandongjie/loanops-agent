package com.loanops.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PolicyAuditMigrationIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void v6CreatesPolicyAuditTablesWithoutRawPolicyOrQueryColumns() {
        assertThat(jdbc.queryForList("""
                SELECT retrieval_id, request_id, conversation_id, decision, as_of_date,
                       query_hash, status, top_k, score_threshold, retrieval_config_hash,
                       embedding_model, index_collection, context_hash, started_at,
                       completed_at, duration_ms, error_type
                FROM policy_retrieval_audit WHERE 1 = 0
                """)).isEmpty();
        assertThat(jdbc.queryForList("""
                SELECT retrieval_id, rank_no, document_id, version_id, chunk_id,
                       match_type, score, selected_for_context, citation_ref, cited_in_answer
                FROM policy_retrieval_hit WHERE 1 = 0
                """)).isEmpty();
    }
}
