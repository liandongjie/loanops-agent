package com.loanops.policy;

import com.loanops.policy.PolicyTypes.DocumentInput;
import com.loanops.policy.PolicyTypes.VersionInput;
import com.loanops.policy.PolicyTypes.VersionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PolicyVersionIdentityIntegrationTest {

    @Autowired private PolicyIngestionService ingestionService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void identicalContentWithDifferentEffectivePeriodIsANewImmutableVersion() {
        DocumentInput document = new DocumentInput(
                "同内容版本示例", "REGULATION", "示例机构", "PUBLIC_FIXTURE", "CN");
        String text = "第四十四条 相同的政策正文。";
        var first = ingestionService.ingest(document, version("A", LocalDate.of(2025, 1, 1), text));
        var second = ingestionService.ingest(document, version("B", LocalDate.of(2026, 1, 1), text));

        assertThat(second.contentHash()).isEqualTo(first.contentHash());
        assertThat(second.versionId()).isNotEqualTo(first.versionId());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM policy_document_version WHERE document_id = ?",
                Integer.class, first.documentId())).isEqualTo(2);
    }

    private VersionInput version(String label, LocalDate effectiveFrom, String text) {
        return new VersionInput(label, "示例令", null, null, effectiveFrom, null,
                VersionStatus.ACTIVE, null, null, text);
    }
}
