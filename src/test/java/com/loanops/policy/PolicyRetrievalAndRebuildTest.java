package com.loanops.policy;

import com.loanops.policy.PolicyTypes.Chunk;
import com.loanops.policy.PolicyTypes.ChunkType;
import com.loanops.policy.PolicyTypes.Document;
import com.loanops.policy.PolicyTypes.MatchType;
import com.loanops.policy.PolicyTypes.StoredChunk;
import com.loanops.policy.PolicyTypes.VectorEntry;
import com.loanops.policy.PolicyTypes.VectorHit;
import com.loanops.policy.PolicyTypes.Version;
import com.loanops.policy.PolicyTypes.VersionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyRetrievalAndRebuildTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 5, 12, 0);
    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 5);

    @Test
    void exactAndSemanticHitsAreDeduplicatedAndParentIsExpanded() {
        StoredChunk parent = stored("parent", null, ChunkType.PARAGRAPH, false, "第四十四条", "上级上下文");
        StoredChunk child = stored("child", "parent", ChunkType.ITEM, true, "第四十四条", "（二）贷后措施");
        FakeRepository repository = new FakeRepository(List.of(child), List.of(parent, child));
        PolicyRetriever retriever = new PolicyRetriever(repository,
                texts -> List.of(new float[]{0.1f, 0.2f}),
                (embedding, date, topK) -> List.of(new VectorHit("child", 0.87)),
                5, 1, 100);

        var result = retriever.retrieve("第四十四条有哪些贷后措施？", AS_OF);

        assertThat(result.hits()).singleElement().satisfies(hit -> {
            assertThat(hit.matchType()).isEqualTo(MatchType.EXACT_AND_SEMANTIC);
            assertThat(hit.score()).isEqualTo(1.0);
            assertThat(hit.parentContext()).extracting(stored -> stored.chunk().chunkId())
                    .containsExactly("parent");
        });
    }

    @Test
    void exactReferenceCombinesQuotedDocumentTitleAndArticle() {
        StoredChunk wrongArticle = stored("wrong", null, ChunkType.ARTICLE, true,
                "第二十五条", "个人信息规则");
        StoredChunk rightArticle = stored("right", null, ChunkType.ARTICLE, true,
                "第二十四条", "还款凭证规则");
        FakeRepository repository = new FakeRepository(
                List.of(wrongArticle, rightArticle), List.of(wrongArticle, rightArticle));
        PolicyRetriever retriever = new PolicyRetriever(repository,
                texts -> List.of(new float[]{0.1f}),
                (embedding, date, topK) -> List.of(), 5, 0, 0);

        var result = retriever.retrieve("《示例政策》第二十四条规定什么？", AS_OF);

        assertThat(result.hits()).singleElement().satisfies(hit -> {
            assertThat(hit.match().chunk().chunkId()).isEqualTo("right");
            assertThat(hit.matchType()).isEqualTo(MatchType.EXACT);
        });
    }
    @Test
    void rebuildReadsCanonicalChunksEmbedsThemAndReplacesDerivedIndex() {
        StoredChunk first = stored("one", null, ChunkType.ARTICLE, true, "第四十四条", "规则一");
        StoredChunk second = stored("two", null, ChunkType.ARTICLE, true, "第四十五条", "规则二");
        FakeRepository repository = new FakeRepository(List.of(first, second), List.of(first, second));
        CapturingIndex index = new CapturingIndex();
        PolicyIndexRebuilder rebuilder = new PolicyIndexRebuilder(repository,
                texts -> texts.stream().map(text -> new float[]{text.length()}).toList(), index);

        int rebuilt = rebuilder.rebuild();

        assertThat(rebuilt).isEqualTo(2);
        assertThat(index.entries).extracting(entry -> entry.storedChunk().chunk().chunkId())
                .containsExactly("one", "two");
        assertThat(index.entries).extracting(entry -> entry.embedding()[0])
                .containsExactly(3.0f, 3.0f);
    }

    private static StoredChunk stored(String id, String parentId, ChunkType type, boolean enabled,
                                      String articleNo, String content) {
        Document document = new Document("document", "示例政策", "REGULATION", "示例机构",
                "PUBLIC_FIXTURE", "CN", NOW, NOW);
        Version version = new Version("version", "document", "v1", "2026年第1号",
                null, null, LocalDate.of(2026, 1, 1), null, VersionStatus.ACTIVE,
                null, "a".repeat(64), null, NOW);
        Chunk chunk = new Chunk(id, "version", parentId, type, "第六章", "贷后管理", articleNo,
                2, type == ChunkType.ITEM ? "（二）" : null,
                "第六章 贷后管理/" + articleNo, 1, content, "b".repeat(64), enabled, NOW);
        return new StoredChunk(chunk, document, version);
    }

    private static final class FakeRepository implements PolicyRepository {
        private final List<StoredChunk> applicable;
        private final List<StoredChunk> all;

        private FakeRepository(List<StoredChunk> applicable, List<StoredChunk> all) {
            this.applicable = applicable;
            this.all = all;
        }

        @Override public void insertDocumentIfAbsent(Document document) { throw new UnsupportedOperationException(); }
        @Override public void insertVersionIfAbsent(Version version) { throw new UnsupportedOperationException(); }
        @Override public void insertChunkIfAbsent(Chunk chunk) { throw new UnsupportedOperationException(); }
        @Override public List<StoredChunk> findApplicable(LocalDate asOfDate) { return applicable; }
        @Override public Optional<StoredChunk> findChunk(String chunkId) {
            return all.stream().filter(stored -> stored.chunk().chunkId().equals(chunkId)).findFirst();
        }
        @Override public List<StoredChunk> findIndexableActiveChunks() { return applicable; }
    }

    private static final class CapturingIndex implements PolicyVectorIndex {
        private List<VectorEntry> entries = new ArrayList<>();
        @Override public void replaceAll(List<VectorEntry> entries) { this.entries = List.copyOf(entries); }
        @Override public List<VectorHit> search(float[] queryEmbedding, LocalDate asOfDate, int topK) {
            return List.of();
        }
    }
}
