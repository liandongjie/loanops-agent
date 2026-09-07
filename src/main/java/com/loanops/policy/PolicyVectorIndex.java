package com.loanops.policy;

import com.loanops.policy.PolicyTypes.VectorEntry;
import com.loanops.policy.PolicyTypes.VectorHit;

import java.time.LocalDate;
import java.util.List;

@FunctionalInterface
public interface PolicyVectorIndex {
    default void replaceAll(List<VectorEntry> entries) {
        throw new UnsupportedOperationException("Index replacement is not supported");
    }

    List<VectorHit> search(float[] queryEmbedding, LocalDate asOfDate, int topK);
}
