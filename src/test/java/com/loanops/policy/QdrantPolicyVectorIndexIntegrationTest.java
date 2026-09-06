package com.loanops.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanops.policy.PolicyTypes.Chunk;
import com.loanops.policy.PolicyTypes.ChunkType;
import com.loanops.policy.PolicyTypes.Document;
import com.loanops.policy.PolicyTypes.StoredChunk;
import com.loanops.policy.PolicyTypes.VectorEntry;
import com.loanops.policy.PolicyTypes.Version;
import com.loanops.policy.PolicyTypes.VersionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "QDRANT_INTEGRATION_TEST", matches = "true")
class QdrantPolicyVectorIndexIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 5, 12, 0);

    @Test
    void rebuiltCollectionAppliesDateFiltersBeforeVectorRanking() {
        QdrantPolicyVectorIndex index = new QdrantPolicyVectorIndex(
                RestClient.builder(), new ObjectMapper(), "http://127.0.0.1:6333",
                "loanops_policy_chunks_it", "");
        index.replaceAll(List.of(
                new VectorEntry(stored("11111111-1111-1111-1111-111111111111",
                        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                        LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)), new float[]{1, 0}),
                new VectorEntry(stored("22222222-2222-2222-2222-222222222222",
                        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                        LocalDate.of(2026, 1, 1), null), new float[]{1, 0})));

        assertThat(index.search(new float[]{1, 0}, LocalDate.of(2025, 6, 1), 5))
                .extracting(PolicyTypes.VectorHit::chunkId)
                .containsExactly("11111111-1111-1111-1111-111111111111");
        assertThat(index.search(new float[]{1, 0}, LocalDate.of(2026, 1, 1), 5))
                .extracting(PolicyTypes.VectorHit::chunkId)
                .containsExactly("22222222-2222-2222-2222-222222222222");
    }

    private StoredChunk stored(String chunkId, String versionId, LocalDate from, LocalDate to) {
        Document document = new Document("document", "示例政策", "REGULATION", "示例机构",
                "PUBLIC_FIXTURE", "CN", NOW, NOW);
        Version version = new Version(versionId, "document", versionId, "2026年第1号",
                null, null, from, to, VersionStatus.ACTIVE, null, "a".repeat(64), null, NOW);
        Chunk chunk = new Chunk(chunkId, versionId, null, ChunkType.ARTICLE,
                "第六章", "贷后管理", "第四十四条", null, null,
                "第六章 贷后管理/第四十四条", 1, "规则", "b".repeat(64), true, NOW);
        return new StoredChunk(chunk, document, version);
    }
}
