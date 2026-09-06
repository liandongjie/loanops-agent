package com.loanops.policy;

import java.util.List;

public interface PolicyEmbeddingProvider {
    List<float[]> embed(List<String> texts);
}
