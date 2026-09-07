package com.loanops.policy;

import com.loanops.policy.PolicyTypes.MatchType;
import com.loanops.policy.PolicyTypes.RetrievalHit;
import com.loanops.policy.PolicyTypes.RetrievalResult;
import com.loanops.policy.PolicyTypes.StoredChunk;
import com.loanops.policy.PolicyTypes.VectorHit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Profile("policy")
public class PolicyRetriever {

    private static final Pattern ARTICLE = Pattern.compile("第[一二三四五六七八九十百千0-9]+条");
    private static final Pattern CHAPTER = Pattern.compile("第[一二三四五六七八九十百千0-9]+章");
    private static final Pattern DOCUMENT_NUMBER = Pattern.compile("\\d{4}年第\\d+号(?:令)?");
    private static final Pattern QUOTED_TITLE = Pattern.compile("《([^》]+)》");

    private final PolicyRepository repository;
    private final PolicyEmbeddingProvider embeddingProvider;
    private final PolicyVectorIndex vectorIndex;
    private final int topK;
    private final int parentDepth;
    private final int parentMaxCharacters;

    public PolicyRetriever(PolicyRepository repository,
                           PolicyEmbeddingProvider embeddingProvider,
                           PolicyVectorIndex vectorIndex,
                           @Value("${loanops.policy.retrieval-top-k:5}") int topK,
                           @Value("${loanops.policy.parent-expansion-depth:1}") int parentDepth,
                           @Value("${loanops.policy.parent-expansion-max-characters:1600}") int parentMaxCharacters) {
        if (topK < 1 || parentDepth < 0 || parentMaxCharacters < 0) {
            throw new IllegalArgumentException("Invalid policy retrieval limits");
        }
        this.repository = repository;
        this.embeddingProvider = embeddingProvider;
        this.vectorIndex = vectorIndex;
        this.topK = topK;
        this.parentDepth = parentDepth;
        this.parentMaxCharacters = parentMaxCharacters;
    }

    public RetrievalResult retrieve(String query, LocalDate asOfDate) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be blank");
        if (asOfDate == null) throw new IllegalArgumentException("asOfDate must not be null");

        List<StoredChunk> applicable = repository.findApplicable(asOfDate);
        Map<String, StoredChunk> applicableById = new LinkedHashMap<>();
        applicable.forEach(chunk -> applicableById.put(chunk.chunk().chunkId(), chunk));
        Map<String, Candidate> merged = new LinkedHashMap<>();

        applicable.stream().filter(chunk -> exactMatch(query, chunk))
                .forEach(chunk -> merged.put(chunk.chunk().chunkId(), new Candidate(chunk, MatchType.EXACT, 1.0)));

        List<float[]> queryVectors = embeddingProvider.embed(List.of(query));
        if (queryVectors.size() != 1) throw new IllegalStateException("Embedding provider returned no query vector");
        for (VectorHit vectorHit : vectorIndex.search(queryVectors.getFirst(), asOfDate, topK)) {
            StoredChunk chunk = applicableById.get(vectorHit.chunkId());
            if (chunk == null) continue;
            merged.compute(vectorHit.chunkId(), (id, existing) -> existing == null
                    ? new Candidate(chunk, MatchType.SEMANTIC, vectorHit.score())
                    : new Candidate(chunk, MatchType.EXACT_AND_SEMANTIC,
                    Math.max(existing.score(), vectorHit.score())));
        }

        List<RetrievalHit> hits = merged.values().stream()
                .sorted((left, right) -> {
                    int exactOrder = Integer.compare(rank(left.matchType()), rank(right.matchType()));
                    return exactOrder != 0 ? exactOrder : Double.compare(right.score(), left.score());
                })
                .limit(topK)
                .map(candidate -> new RetrievalHit(candidate.chunk(), candidate.matchType(), candidate.score(),
                        expandParents(candidate.chunk())))
                .toList();
        return new RetrievalResult(query, asOfDate, hits);
    }

    private List<StoredChunk> expandParents(StoredChunk hit) {
        List<StoredChunk> parents = new ArrayList<>();
        String parentId = hit.chunk().parentChunkId();
        int characters = 0;
        for (int depth = 0; depth < parentDepth && parentId != null; depth++) {
            StoredChunk parent = repository.findChunk(parentId).orElse(null);
            if (parent == null || characters + parent.chunk().content().length() > parentMaxCharacters) break;
            parents.add(parent);
            characters += parent.chunk().content().length();
            parentId = parent.chunk().parentChunkId();
        }
        return List.copyOf(parents);
    }

    private static boolean exactMatch(String query, StoredChunk stored) {
        String article = find(ARTICLE, query);
        String chapter = find(CHAPTER, query);
        String documentNumber = find(DOCUMENT_NUMBER, query);
        String quotedTitle = findGroup(QUOTED_TITLE, query, 1);
        if (article != null || chapter != null || documentNumber != null || quotedTitle != null) {
            return (article == null || article.equals(stored.chunk().articleNo()))
                    && (chapter == null || chapter.equals(stored.chunk().chapterNo()))
                    && (documentNumber == null || stored.version().documentNumber() != null
                    && stored.version().documentNumber().contains(documentNumber))
                    && (quotedTitle == null || quotedTitle.equals(stored.document().title()));
        }
        return query.contains(stored.document().title());
    }

    private static String find(Pattern pattern, String query) {
        Matcher matcher = pattern.matcher(query);
        return matcher.find() ? matcher.group() : null;
    }

    private static String findGroup(Pattern pattern, String query, int group) {
        Matcher matcher = pattern.matcher(query);
        return matcher.find() ? matcher.group(group) : null;
    }

    private static int rank(MatchType type) {
        return switch (type) {
            case EXACT_AND_SEMANTIC -> 0;
            case EXACT -> 1;
            case SEMANTIC -> 2;
        };
    }

    private record Candidate(StoredChunk chunk, MatchType matchType, double score) {}
}
