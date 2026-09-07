package com.loanops.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanops.policy.PolicyTypes.StoredChunk;
import com.loanops.policy.PolicyTypes.VectorEntry;
import com.loanops.policy.PolicyTypes.VectorHit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("policy")
public class QdrantPolicyVectorIndex implements PolicyVectorIndex {

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String collection;

    public QdrantPolicyVectorIndex(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${loanops.policy.qdrant.base-url:http://localhost:6333}") String baseUrl,
            @Value("${loanops.policy.qdrant.collection:loanops_policy_chunks}") String collection,
            @Value("${loanops.policy.qdrant.api-key:}") String apiKey) {
        RestClient.Builder configured = builder.baseUrl(baseUrl);
        if (!apiKey.isBlank()) configured.defaultHeader("api-key", apiKey);
        this.client = configured.build();
        this.objectMapper = objectMapper;
        this.collection = collection;
    }

    @Override
    public void replaceAll(List<VectorEntry> entries) {
        deleteCollection();
        if (entries.isEmpty()) return;
        int dimensions = entries.getFirst().embedding().length;
        if (dimensions == 0) throw new IllegalArgumentException("Embedding vectors must not be empty");
        if (entries.stream().anyMatch(entry -> entry.embedding().length != dimensions)) {
            throw new IllegalArgumentException("Embedding vector dimensions must match");
        }

        client.put().uri("/collections/{collection}", collection)
                .body(Map.of("vectors", Map.of("size", dimensions, "distance", "Cosine")))
                .retrieve().toBodilessEntity();

        List<Map<String, Object>> points = entries.stream().map(this::point).toList();
        client.put().uri("/collections/{collection}/points?wait=true", collection)
                .body(Map.of("points", points))
                .retrieve().toBodilessEntity();
    }

    @Override
    public List<VectorHit> search(float[] queryEmbedding, LocalDate asOfDate, int topK) {
        long epochDay = asOfDate.toEpochDay();
        Map<String, Object> filter = Map.of("must", List.of(
                Map.of("key", "status", "match", Map.of("value", "ACTIVE")),
                Map.of("key", "effectiveFromEpochDay", "range", Map.of("lte", epochDay)),
                Map.of("key", "effectiveToEpochDay", "range", Map.of("gt", epochDay))));
        Map<String, Object> request = Map.of(
                "query", queryEmbedding,
                "filter", filter,
                "limit", topK,
                "with_payload", false);
        String json = client.post().uri("/collections/{collection}/points/query", collection)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(request).retrieve().body(String.class);
        try {
            JsonNode points = objectMapper.readTree(json).path("result").path("points");
            List<VectorHit> hits = new ArrayList<>();
            for (JsonNode point : points) {
                hits.add(new VectorHit(point.path("id").asText(), point.path("score").asDouble()));
            }
            return List.copyOf(hits);
        } catch (Exception invalidResponse) {
            throw new IllegalStateException("Invalid Qdrant query response", invalidResponse);
        }
    }

    private void deleteCollection() {
        try {
            client.delete().uri("/collections/{collection}", collection)
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // A missing derived collection is already clean.
        }
    }

    private Map<String, Object> point(VectorEntry entry) {
        StoredChunk stored = entry.storedChunk();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunkId", stored.chunk().chunkId());
        payload.put("documentId", stored.document().documentId());
        payload.put("versionId", stored.version().versionId());
        putIfPresent(payload, "documentNumber", stored.version().documentNumber());
        payload.put("articleNo", stored.chunk().articleNo());
        putIfPresent(payload, "paragraphNo", stored.chunk().paragraphNo());
        putIfPresent(payload, "itemNo", stored.chunk().itemNo());
        payload.put("sourceType", stored.document().sourceType());
        payload.put("status", stored.version().status().name());
        payload.put("effectiveFromEpochDay", stored.version().effectiveFrom().toEpochDay());
        payload.put("effectiveToEpochDay", stored.version().effectiveTo() == null
                ? Long.MAX_VALUE : stored.version().effectiveTo().toEpochDay());
        return Map.of("id", stored.chunk().chunkId(), "vector", entry.embedding(), "payload", payload);
    }

    private static void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) payload.put(key, value);
    }
}
