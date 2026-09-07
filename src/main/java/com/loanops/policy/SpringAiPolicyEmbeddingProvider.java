package com.loanops.policy;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("policy")
public class SpringAiPolicyEmbeddingProvider implements PolicyEmbeddingProvider {

    private final EmbeddingModel embeddingModel;

    public SpringAiPolicyEmbeddingProvider(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream().map(embeddingModel::embed).toList();
    }
}
