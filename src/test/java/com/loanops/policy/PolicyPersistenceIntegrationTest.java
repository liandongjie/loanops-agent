package com.loanops.policy;

import com.loanops.policy.PolicyTypes.DocumentInput;
import com.loanops.policy.PolicyTypes.IngestionResult;
import com.loanops.policy.PolicyTypes.RetrievalResult;
import com.loanops.policy.PolicyTypes.VersionInput;
import com.loanops.policy.PolicyTypes.VersionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PolicyPersistenceIntegrationTest {

    private static final DocumentInput DOCUMENT = new DocumentInput(
            "示例公开政策", "REGULATION", "示例监管机构", "PUBLIC_FIXTURE", "CN");

    @Autowired
    private PolicyIngestionService ingestionService;

    @Autowired
    private PolicyRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void repeatedIngestionIsIdempotentAndChangedContentCreatesANewImmutableVersion() {
        VersionInput original = version("A", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1),
                "第四十四条 旧版本规则。");
        IngestionResult first = ingestionService.ingest(DOCUMENT, original);
        IngestionResult repeated = ingestionService.ingest(DOCUMENT, original);
        IngestionResult changed = ingestionService.ingest(DOCUMENT,
                version("A-corrected", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1),
                        "第四十四条 修改后的规则。"));

        assertThat(repeated).isEqualTo(first);
        assertThat(changed.documentId()).isEqualTo(first.documentId());
        assertThat(changed.versionId()).isNotEqualTo(first.versionId());
        assertThat(changed.contentHash()).isNotEqualTo(first.contentHash());
        assertThat(count("policy_document")).isEqualTo(1);
        assertThat(count("policy_document_version")).isEqualTo(2);
        assertThat(count("policy_chunk")).isEqualTo(2);
    }

    @Test
    void exactArticleRetrievalRespectsHalfOpenVersionApplicability() {
        IngestionResult versionA = ingestionService.ingest(DOCUMENT,
                version("A", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1),
                        "第四十四条 旧版本适用。"));
        IngestionResult versionB = ingestionService.ingest(DOCUMENT,
                version("B", LocalDate.of(2026, 1, 1), null,
                        "第四十四条 新版本适用。"));
        PolicyRetriever retriever = new PolicyRetriever(repository,
                texts -> List.of(new float[]{1.0f}), (embedding, date, topK) -> List.of(),
                5, 1, 1000);

        RetrievalResult in2025 = retriever.retrieve("请查第四十四条", LocalDate.of(2025, 8, 1));
        RetrievalResult in2026 = retriever.retrieve("请查第四十四条", LocalDate.of(2026, 1, 1));

        assertThat(in2025.hits()).singleElement()
                .satisfies(hit -> assertThat(hit.match().version().versionId()).isEqualTo(versionA.versionId()));
        assertThat(in2026.hits()).singleElement()
                .satisfies(hit -> assertThat(hit.match().version().versionId()).isEqualTo(versionB.versionId()));
    }

    @Test
    void flywayCreatesAllCanonicalPolicyTables() {
        assertThat(jdbc.queryForList("SELECT document_id FROM policy_document WHERE 1 = 0")).isEmpty();
        assertThat(jdbc.queryForList("SELECT version_id FROM policy_document_version WHERE 1 = 0")).isEmpty();
        assertThat(jdbc.queryForList("SELECT chunk_id FROM policy_chunk WHERE 1 = 0")).isEmpty();
    }

    private VersionInput version(String label, LocalDate from, LocalDate to, String text) {
        return new VersionInput(label, "示例令 2025年第1号", null, null, from, to,
                VersionStatus.ACTIVE, "https://example.invalid/policy", null, text);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
