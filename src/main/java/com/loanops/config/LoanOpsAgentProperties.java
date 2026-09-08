package com.loanops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("loanops.agent")
public record LoanOpsAgentProperties(String provider, String adapter, String model) {

    public LoanOpsAgentProperties {
        provider = requireText(provider, "provider");
        adapter = requireText(adapter, "adapter");
        model = requireText(model, "model");
        if (model.length() > 128) {
            throw new IllegalArgumentException("loanops.agent.model must not exceed 128 characters");
        }

        String expectedAdapter = switch (provider) {
            case "deepseek" -> "deepseek";
            case "ollama" -> "ollama";
            case "glm" -> "openai";
            default -> throw new IllegalArgumentException("Unknown loanops.agent.provider: '" + provider + "'");
        };
        if (!expectedAdapter.equals(adapter)) {
            throw new IllegalArgumentException("Invalid loanops.agent provider/adapter mapping: provider='"
                    + provider + "', adapter='" + adapter + "', expected mapping '"
                    + provider + " -> " + expectedAdapter + "'");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("loanops.agent." + name + " must not be blank");
        }
        return value.trim();
    }
}
