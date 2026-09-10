package com.loanops.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanops.policy.PolicyTypes.DocumentInput;
import com.loanops.policy.PolicyTypes.VectorEntry;
import com.loanops.policy.PolicyTypes.VectorHit;
import com.loanops.policy.PolicyTypes.VersionInput;
import com.loanops.policy.PolicyTypes.VersionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LocalPolicyBootstrapIntegrationTest {

    private static final ClassPathResource CORPUS =
            new ClassPathResource("policy/demo-policy-corpus.json");

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PolicyIngestionService ingestionService;
    @Autowired private PolicyRepository repository;

    @Test
    void freshBootstrapPersistsCanonicalCorpusAndBuildsDerivedIndex() {
        CapturingIndex index = new CapturingIndex();

        LocalPolicyBootstrapService.BootstrapResult result = service(index).bootstrap(CORPUS);

        assertThat(result.documentCount()).isEqualTo(4);
        assertThat(result.versionCount()).isEqualTo(5);
        assertThat(result.chunkCount()).isEqualTo(41);
        assertThat(result.indexedCount()).isEqualTo(41);
        assertThat(index.entries).hasSize(41);
        assertThat(index.entries).extracting(entry -> entry.storedChunk().chunk().chunkId())
                .containsExactlyElementsOf(repository.findIndexableActiveChunks().stream()
                        .map(stored -> stored.chunk().chunkId()).toList());
    }

    @Test
    void repeatedBootstrapKeepsStableIdsAndCounts() {
        CapturingIndex index = new CapturingIndex();
        LocalPolicyBootstrapService service = service(index);
        service.bootstrap(CORPUS);
        List<String> firstIds = allIds();

        LocalPolicyBootstrapService.BootstrapResult repeated = service.bootstrap(CORPUS);

        assertThat(repeated.documentCount()).isEqualTo(4);
        assertThat(repeated.versionCount()).isEqualTo(5);
        assertThat(repeated.chunkCount()).isEqualTo(41);
        assertThat(allIds()).containsExactlyElementsOf(firstIds);
        assertThat(index.replaceCount).isEqualTo(2);
    }

    @Test
    void partialDemoDataIsRecoveredWithoutDuplicates() {
        CapturingIndex index = new CapturingIndex();
        LocalPolicyBootstrapService service = service(index);
        service.bootstrap(CORPUS);
        String removedDocumentId = jdbc.queryForObject(
                "SELECT document_id FROM policy_document WHERE title = ?", String.class, "贷款运营控制评测规程");
        jdbc.update("DELETE FROM policy_chunk WHERE version_id IN "
                + "(SELECT version_id FROM policy_document_version WHERE document_id = ?)", removedDocumentId);
        jdbc.update("DELETE FROM policy_document_version WHERE document_id = ?", removedDocumentId);
        jdbc.update("DELETE FROM policy_document WHERE document_id = ?", removedDocumentId);

        LocalPolicyBootstrapService.BootstrapResult recovered = service.bootstrap(CORPUS);

        assertThat(recovered.documentCount()).isEqualTo(4);
        assertThat(recovered.versionCount()).isEqualTo(5);
        assertThat(recovered.chunkCount()).isEqualTo(41);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT document_id) FROM policy_document", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void isolatedMissingChunkIsRecoveredWithoutDuplicates() {
        CapturingIndex index = new CapturingIndex();
        LocalPolicyBootstrapService service = service(index);
        service.bootstrap(CORPUS);
        List<String> firstIds = allIds();
        String removedChunkId = jdbc.queryForObject(
                "SELECT chunk_id FROM policy_chunk ORDER BY chunk_id LIMIT 1", String.class);

        assertThat(jdbc.update("DELETE FROM policy_chunk WHERE chunk_id = ?", removedChunkId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM policy_document", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM policy_document_version", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM policy_chunk", Integer.class)).isEqualTo(40);

        LocalPolicyBootstrapService.BootstrapResult recovered = service.bootstrap(CORPUS);

        assertThat(recovered.documentCount()).isEqualTo(4);
        assertThat(recovered.versionCount()).isEqualTo(5);
        assertThat(recovered.chunkCount()).isEqualTo(41);
        assertThat(allIds()).containsExactlyElementsOf(firstIds);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT document_id) FROM policy_document", Integer.class))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT version_id) FROM policy_document_version", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT chunk_id) FROM policy_chunk", Integer.class))
                .isEqualTo(41);
    }

    @Test
    void unknownExistingPolicyRefusesBeforeDemoWritesOrIndexRebuild() {
        ingestionService.ingest(
                new DocumentInput("用户导入政策", "REGULATION", "用户机构", "USER_IMPORTED", "CN"),
                new VersionInput("v1", "USER-1", null, null, LocalDate.of(2026, 1, 1), null,
                        VersionStatus.ACTIVE, "urn:user:policy", null, "第一条 用户自己的政策。"));
        CapturingIndex index = new CapturingIndex();

        assertThatThrownBy(() -> service(index).bootstrap(CORPUS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bootstrap refused")
                .hasMessageContaining("no data or Qdrant index was changed");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM policy_document", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM policy_document WHERE source_type = ?",
                Integer.class, LocalPolicyBootstrapService.LOCAL_DEMO_SOURCE_TYPE)).isZero();
        assertThat(index.replaceCount).isZero();
    }

    @Test
    void unknownVersionUnderDemoDocumentIsAlsoRefusedBeforeRebuild() {
        CapturingIndex index = new CapturingIndex();
        LocalPolicyBootstrapService service = service(index);
        service.bootstrap(CORPUS);
        ingestionService.ingest(
                new DocumentInput("贷后管理评测规程", "DEMO_POLICY", "LoanOps Evaluation",
                        LocalPolicyBootstrapService.LOCAL_DEMO_SOURCE_TYPE, "CN"),
                new VersionInput("用户版本", "USER-VERSION", null, null, LocalDate.of(2027, 1, 1), null,
                        VersionStatus.ACTIVE, "urn:user:version", null, "第一条 用户自己的政策。"));

        assertThatThrownBy(() -> service.bootstrap(CORPUS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown policy version");

        assertThat(index.replaceCount).isEqualTo(1);
    }

    @Test
    void malformedCorpusFailsBeforeWritesOrIndexRebuild() {
        CapturingIndex index = new CapturingIndex();
        ByteArrayResource malformed = new ByteArrayResource("{not-json".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service(index).bootstrap(malformed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("corpus is malformed");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM policy_document", Integer.class)).isZero();
        assertThat(index.replaceCount).isZero();
    }

    @Test
    void indexFailureLeavesRetryableCanonicalDemoData() {
        PolicyVectorIndex failing = new PolicyVectorIndex() {
            @Override public void replaceAll(List<VectorEntry> entries) {
                throw new IllegalStateException("Qdrant unavailable");
            }
            @Override public List<VectorHit> search(float[] queryEmbedding, LocalDate asOfDate, int topK) {
                return List.of();
            }
        };

        assertThatThrownBy(() -> service(failing).bootstrap(CORPUS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("index rebuild failed")
                .hasMessageContaining("Ollama, bge-m3, Qdrant");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM policy_document", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM policy_chunk", Integer.class)).isEqualTo(41);
    }

    private LocalPolicyBootstrapService service(PolicyVectorIndex index) {
        PolicyEmbeddingProvider embeddings = texts -> texts.stream()
                .map(text -> new float[]{text.length(), 1.0f}).toList();
        return new LocalPolicyBootstrapService(objectMapper, jdbc, ingestionService,
                new PolicyIndexRebuilder(repository, embeddings, index));
    }

    private List<String> allIds() {
        List<String> ids = new ArrayList<>();
        ids.addAll(jdbc.queryForList("SELECT document_id FROM policy_document ORDER BY document_id", String.class));
        ids.addAll(jdbc.queryForList("SELECT version_id FROM policy_document_version ORDER BY version_id", String.class));
        ids.addAll(jdbc.queryForList("SELECT chunk_id FROM policy_chunk ORDER BY chunk_id", String.class));
        return ids;
    }

    private static final class CapturingIndex implements PolicyVectorIndex {
        private List<VectorEntry> entries = List.of();
        private int replaceCount;
        @Override public void replaceAll(List<VectorEntry> entries) {
            this.entries = List.copyOf(entries);
            replaceCount++;
        }
        @Override public List<VectorHit> search(float[] queryEmbedding, LocalDate asOfDate, int topK) {
            return List.of();
        }
    }
}
