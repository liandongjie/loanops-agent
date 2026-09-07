package com.loanops.policy;

import com.loanops.policy.PolicyTypes.Chunk;
import com.loanops.policy.PolicyTypes.Document;
import com.loanops.policy.PolicyTypes.StoredChunk;
import com.loanops.policy.PolicyTypes.Version;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PolicyRepository {
    void insertDocumentIfAbsent(Document document);
    void insertVersionIfAbsent(Version version);
    void insertChunkIfAbsent(Chunk chunk);
    List<StoredChunk> findApplicable(LocalDate asOfDate);
    Optional<StoredChunk> findChunk(String chunkId);
    List<StoredChunk> findIndexableActiveChunks();
}
