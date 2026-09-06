package com.loanops.policy;

import com.loanops.policy.PolicyTypes.StoredChunk;
import com.loanops.policy.PolicyTypes.VectorEntry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Profile("policy")
public class PolicyIndexRebuilder {

    private final PolicyRepository repository;
    private final PolicyEmbeddingProvider embeddingProvider;
    private final PolicyVectorIndex vectorIndex;

    public PolicyIndexRebuilder(PolicyRepository repository, PolicyEmbeddingProvider embeddingProvider,
                                PolicyVectorIndex vectorIndex) {
        this.repository = repository;
        this.embeddingProvider = embeddingProvider;
        this.vectorIndex = vectorIndex;
    }

    public int rebuild() {
        List<StoredChunk> chunks = repository.findIndexableActiveChunks();
        List<float[]> embeddings = embeddingProvider.embed(
                chunks.stream().map(chunk -> chunk.chunk().content()).toList());
        if (embeddings.size() != chunks.size()) {
            throw new IllegalStateException("Embedding provider returned an unexpected vector count");
        }
        List<VectorEntry> entries = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            entries.add(new VectorEntry(chunks.get(index), embeddings.get(index)));
        }
        vectorIndex.replaceAll(entries);
        return entries.size();
    }
}
