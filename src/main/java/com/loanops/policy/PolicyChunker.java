package com.loanops.policy;

import com.loanops.policy.PolicyTypes.Chunk;
import com.loanops.policy.PolicyTypes.ChunkType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PolicyChunker {

    private static final Pattern CHAPTER = Pattern.compile("^(第[一二三四五六七八九十百千0-9]+章)\\s*(.*)$");
    private static final Pattern ARTICLE = Pattern.compile("^(第[一二三四五六七八九十百千0-9]+条)\\s*(.*)$");
    private static final Pattern ITEM = Pattern.compile("^[（(]([一二三四五六七八九十百千0-9]+)[）)]\\s*(.*)$");

    private final int maxCharacters;

    public PolicyChunker(@Value("${loanops.policy.chunk-max-characters:1200}") int maxCharacters) {
        if (maxCharacters < 20) {
            throw new IllegalArgumentException("Policy chunk size must be at least 20");
        }
        this.maxCharacters = maxCharacters;
    }

    public List<Chunk> chunk(String versionId, String structuredText, LocalDateTime createdAt) {
        List<Chunk> chunks = new ArrayList<>();
        int[] ordinal = {0};
        for (Article article : parse(structuredText)) {
            addArticle(versionId, article, createdAt, chunks, ordinal);
        }
        return List.copyOf(chunks);
    }

    private void addArticle(String versionId, Article article, LocalDateTime createdAt,
                            List<Chunk> chunks, int[] ordinal) {
        String articlePath = path(article, null, null);
        String articleContent = article.header() + "\n" + String.join("\n", article.lines());
        if (articleContent.length() <= maxCharacters) {
            chunks.add(newChunk(versionId, null, ChunkType.ARTICLE, article, null, null,
                    articlePath, articleContent, true, createdAt, ordinal));
            return;
        }

        Chunk articleParent = newChunk(versionId, null, ChunkType.ARTICLE, article, null, null,
                articlePath, articleContent, false, createdAt, ordinal);
        chunks.add(articleParent);
        for (Paragraph paragraph : paragraphs(article.lines())) {
            addParagraph(versionId, article, articleParent, paragraph, createdAt, chunks, ordinal);
        }
    }

    private void addParagraph(String versionId, Article article, Chunk articleParent, Paragraph paragraph,
                              LocalDateTime createdAt, List<Chunk> chunks, int[] ordinal) {
        String paragraphPath = path(article, paragraph.number(), null);
        String content = String.join("\n", paragraph.lines());
        if (paragraph.items().isEmpty() && content.length() <= maxCharacters) {
            chunks.add(newChunk(versionId, articleParent.chunkId(), ChunkType.PARAGRAPH, article,
                    paragraph.number(), null, paragraphPath, content, true, createdAt, ordinal));
            return;
        }

        Chunk paragraphParent = newChunk(versionId, articleParent.chunkId(), ChunkType.PARAGRAPH, article,
                paragraph.number(), null, paragraphPath, content, false, createdAt, ordinal);
        chunks.add(paragraphParent);
        if (paragraph.items().isEmpty()) {
            addFragments(versionId, article, paragraph.number(), null, paragraphParent,
                    content, createdAt, chunks, ordinal);
            return;
        }
        for (Item item : paragraph.items()) {
            String itemContent = item.label() + item.content();
            String itemPath = path(article, paragraph.number(), item.label());
            if (itemContent.length() <= maxCharacters) {
                chunks.add(newChunk(versionId, paragraphParent.chunkId(), ChunkType.ITEM, article,
                        paragraph.number(), item.label(), itemPath, itemContent, true, createdAt, ordinal));
            } else {
                Chunk itemParent = newChunk(versionId, paragraphParent.chunkId(), ChunkType.ITEM, article,
                        paragraph.number(), item.label(), itemPath, itemContent, false, createdAt, ordinal);
                chunks.add(itemParent);
                addFragments(versionId, article, paragraph.number(), item.label(), itemParent,
                        itemContent, createdAt, chunks, ordinal);
            }
        }
    }

    private void addFragments(String versionId, Article article, Integer paragraphNo, String itemNo,
                              Chunk parent, String content, LocalDateTime createdAt,
                              List<Chunk> chunks, int[] ordinal) {
        List<String> fragments = splitWithinStructure(content);
        for (int index = 0; index < fragments.size(); index++) {
            chunks.add(newChunk(versionId, parent.chunkId(), ChunkType.FRAGMENT, article,
                    paragraphNo, itemNo, parent.sectionPath() + "/fragment-" + (index + 1),
                    fragments.get(index), true, createdAt, ordinal));
        }
    }

    private List<String> splitWithinStructure(String content) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : content.split("(?<=[。！？；])")) {
            if (sentence.length() > maxCharacters) {
                flush(current, result);
                for (int start = 0; start < sentence.length(); start += maxCharacters) {
                    result.add(sentence.substring(start, Math.min(sentence.length(), start + maxCharacters)));
                }
            } else if (!current.isEmpty() && current.length() + sentence.length() > maxCharacters) {
                flush(current, result);
                current.append(sentence);
            } else {
                current.append(sentence);
            }
        }
        flush(current, result);
        return result;
    }

    private static void flush(StringBuilder current, List<String> result) {
        if (!current.isEmpty()) {
            result.add(current.toString());
            current.setLength(0);
        }
    }

    private Chunk newChunk(String versionId, String parentId, ChunkType type, Article article,
                           Integer paragraphNo, String itemNo, String path, String content,
                           boolean retrievalEnabled, LocalDateTime createdAt, int[] ordinal) {
        String normalized = PolicyHashing.normalize(content);
        String hash = PolicyHashing.sha256(normalized);
        int nextOrdinal = ++ordinal[0];
        String identity = versionId + "|" + type + "|" + path + "|" + hash;
        return new Chunk(PolicyHashing.stableId("policy-chunk", identity), versionId, parentId, type,
                article.chapterNo(), article.chapterTitle(), article.number(), paragraphNo, itemNo,
                path, nextOrdinal, normalized, hash, retrievalEnabled, createdAt);
    }

    private static String path(Article article, Integer paragraphNo, String itemNo) {
        List<String> parts = new ArrayList<>();
        if (article.chapterNo() != null) {
            parts.add(article.chapterNo() + (article.chapterTitle().isBlank() ? "" : " " + article.chapterTitle()));
        }
        parts.add(article.number());
        if (paragraphNo != null) parts.add("第" + paragraphNo + "款");
        if (itemNo != null) parts.add(itemNo);
        return String.join("/", parts);
    }

    private static List<Article> parse(String text) {
        String chapterNo = null;
        String chapterTitle = null;
        ArticleBuilder current = null;
        List<Article> result = new ArrayList<>();
        for (String raw : PolicyHashing.normalize(text).split("\\n")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            Matcher chapter = CHAPTER.matcher(line);
            if (chapter.matches()) {
                if (current != null) result.add(current.build());
                current = null;
                chapterNo = chapter.group(1);
                chapterTitle = chapter.group(2);
                continue;
            }
            Matcher article = ARTICLE.matcher(line);
            if (article.matches()) {
                if (current != null) result.add(current.build());
                current = new ArticleBuilder(chapterNo, chapterTitle, article.group(1), article.group(1));
                if (!article.group(2).isBlank()) current.lines.add(article.group(2));
            } else if (current != null) {
                current.lines.add(line);
            }
        }
        if (current != null) result.add(current.build());
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Policy text must contain at least one Article heading");
        }
        return result;
    }

    private static List<Paragraph> paragraphs(List<String> lines) {
        List<Paragraph> result = new ArrayList<>();
        ParagraphBuilder current = null;
        int number = 0;
        for (String line : lines) {
            Matcher item = ITEM.matcher(line);
            if (item.matches()) {
                if (current == null) current = new ParagraphBuilder(++number);
                current.items.add(new Item("（" + item.group(1) + "）", item.group(2)));
            } else {
                if (current != null) result.add(current.build());
                current = new ParagraphBuilder(++number);
                current.lines.add(line);
            }
        }
        if (current != null) result.add(current.build());
        return result;
    }

    private record Article(String chapterNo, String chapterTitle, String number,
                           String header, List<String> lines) {}
    private record Paragraph(int number, List<String> lines, List<Item> items) {}
    private record Item(String label, String content) {}

    private static final class ArticleBuilder {
        private final String chapterNo;
        private final String chapterTitle;
        private final String number;
        private final String header;
        private final List<String> lines = new ArrayList<>();

        private ArticleBuilder(String chapterNo, String chapterTitle, String number, String header) {
            this.chapterNo = chapterNo;
            this.chapterTitle = chapterTitle;
            this.number = number;
            this.header = header;
        }

        private Article build() {
            return new Article(chapterNo, chapterTitle, number, header, List.copyOf(lines));
        }
    }

    private static final class ParagraphBuilder {
        private final int number;
        private final List<String> lines = new ArrayList<>();
        private final List<Item> items = new ArrayList<>();

        private ParagraphBuilder(int number) { this.number = number; }

        private Paragraph build() {
            List<String> content = new ArrayList<>(lines);
            items.forEach(item -> content.add(item.label() + item.content()));
            return new Paragraph(number, List.copyOf(content), List.copyOf(items));
        }
    }
}
