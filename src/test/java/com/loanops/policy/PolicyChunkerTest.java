package com.loanops.policy;

import com.loanops.policy.PolicyTypes.Chunk;
import com.loanops.policy.PolicyTypes.ChunkType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyChunkerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 5, 12, 0);

    @Test
    void adjacentArticlesNeverShareAChunkAndIdentityIsStable() {
        PolicyChunker chunker = new PolicyChunker(200);
        String text = """
                第六章 贷后管理
                第四十四条 贷款人应当持续跟踪贷款用途。
                第四十五条 贷款人应当保存贷后检查记录。
                """;

        List<Chunk> first = chunker.chunk("version-a", text, NOW);
        List<Chunk> second = chunker.chunk("version-a", text, NOW.plusDays(1));

        assertThat(first).hasSize(2);
        assertThat(first).extracting(Chunk::articleNo).containsExactly("第四十四条", "第四十五条");
        assertThat(first.get(0).content()).doesNotContain("第四十五条");
        assertThat(first.get(1).content()).doesNotContain("第四十四条");
        assertThat(second).extracting(Chunk::chunkId)
                .containsExactlyElementsOf(first.stream().map(Chunk::chunkId).toList());
        assertThat(second).extracting(Chunk::contentHash)
                .containsExactlyElementsOf(first.stream().map(Chunk::contentHash).toList());
    }

    @Test
    void longArticleSplitsWithinParagraphAndItemWithCorrectParentsAndPaths() {
        PolicyChunker chunker = new PolicyChunker(45);
        String text = """
                第六章 贷后管理
                第四十四条 贷款人应当建立贷后管理制度并明确持续跟踪的职责分工。
                贷款人发现风险后应当及时评估并完整保存检查记录。
                （一）核实贷款用途、还款来源以及借款人经营情况。
                （二）根据风险变化采取与制度相符的贷后管理措施。
                """;

        List<Chunk> chunks = chunker.chunk("version-a", text, NOW);
        Chunk article = chunks.stream().filter(chunk -> chunk.chunkType() == ChunkType.ARTICLE).findFirst().orElseThrow();
        Chunk secondParagraph = chunks.stream()
                .filter(chunk -> chunk.chunkType() == ChunkType.PARAGRAPH && Integer.valueOf(2).equals(chunk.paragraphNo()))
                .findFirst().orElseThrow();
        List<Chunk> itemDescendants = chunks.stream()
                .filter(chunk -> Integer.valueOf(2).equals(chunk.paragraphNo()) && chunk.itemNo() != null)
                .toList();

        assertThat(article.retrievalEnabled()).isFalse();
        assertThat(secondParagraph.parentChunkId()).isEqualTo(article.chunkId());
        assertThat(secondParagraph.sectionPath()).isEqualTo("第六章 贷后管理/第四十四条/第2款");
        assertThat(itemDescendants).isNotEmpty().allSatisfy(chunk -> {
            assertThat(chunk.articleNo()).isEqualTo("第四十四条");
            assertThat(chunk.sectionPath()).startsWith(secondParagraph.sectionPath());
        });
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content()).doesNotContain("第四十五条"));
        assertThat(chunks.stream().filter(Chunk::retrievalEnabled))
                .allSatisfy(chunk -> assertThat(chunk.content().length()).isLessThanOrEqualTo(45));
    }
}
