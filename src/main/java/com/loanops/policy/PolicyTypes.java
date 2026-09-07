package com.loanops.policy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PolicyTypes {

    private PolicyTypes() {}

    public enum VersionStatus { DRAFT, ACTIVE, INVALID }
    public enum ChunkType { ARTICLE, PARAGRAPH, ITEM, FRAGMENT }
    public enum MatchType { EXACT, SEMANTIC, EXACT_AND_SEMANTIC }

    public record DocumentInput(String title, String documentType, String issuer,
                                String sourceType, String jurisdiction) {}

    public record VersionInput(String versionLabel, String documentNumber,
                               LocalDate issuedAt, LocalDate publishedAt,
                               LocalDate effectiveFrom, LocalDate effectiveTo,
                               VersionStatus status, String sourceUri,
                               String supersedesVersionId, String structuredText) {}

    public record Document(String documentId, String title, String documentType, String issuer,
                           String sourceType, String jurisdiction,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record Version(String versionId, String documentId, String versionLabel, String documentNumber,
                          LocalDate issuedAt, LocalDate publishedAt,
                          LocalDate effectiveFrom, LocalDate effectiveTo,
                          VersionStatus status, String sourceUri, String contentHash,
                          String supersedesVersionId, LocalDateTime createdAt) {
        public boolean appliesOn(LocalDate date) {
            return status == VersionStatus.ACTIVE
                    && !effectiveFrom.isAfter(date)
                    && (effectiveTo == null || date.isBefore(effectiveTo));
        }
    }

    public record Chunk(String chunkId, String versionId, String parentChunkId, ChunkType chunkType,
                        String chapterNo, String chapterTitle, String articleNo,
                        Integer paragraphNo, String itemNo, String sectionPath,
                        int ordinal, String content, String contentHash,
                        boolean retrievalEnabled, LocalDateTime createdAt) {}

    public record StoredChunk(Chunk chunk, Document document, Version version) {}
    public record IngestionResult(String documentId, String versionId, String contentHash, int chunkCount) {}
    public record VectorEntry(StoredChunk storedChunk, float[] embedding) {}
    public record VectorHit(String chunkId, double score) {}
    public record RetrievalHit(StoredChunk match, MatchType matchType, double score,
                               List<StoredChunk> parentContext) {}
    public record RetrievalResult(String query, LocalDate asOfDate, List<RetrievalHit> hits) {}
}
