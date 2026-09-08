package com.loanops.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiChatModelBindingTest {

    @Test
    void bindsRuntimeModelToDeepSeekChatOptions() {
        try (ConfigurableApplicationContext context = run("deepseek", "deepseek", "test-deepseek-model")) {
            assertThat(context.getBean(DeepSeekChatProperties.class).getOptions().getModel())
                    .isEqualTo("test-deepseek-model");
        }
    }

    @Test
    void bindsRuntimeModelToOllamaChatOptions() {
        try (ConfigurableApplicationContext context = run("ollama", "ollama", "test-ollama-model")) {
            assertThat(context.getBean(OllamaChatProperties.class).getOptions().getModel())
                    .isEqualTo("test-ollama-model");
        }
    }

    @Test
    void keepsOllamaChatAndEmbeddingConfigurationIndependent() {
        try (ConfigurableApplicationContext context = runWithPolicy()) {
            assertThat(context.getBean(OllamaChatProperties.class).getOptions().getModel())
                    .isEqualTo("qwen3:4b");
            assertThat(context.getBean(OllamaEmbeddingProperties.class).getModel())
                    .isEqualTo("bge-m3");
            assertThat(context.getBean(OllamaConnectionProperties.class).getBaseUrl())
                    .isEqualTo("http://127.0.0.1:11435");
        }
    }

    private ConfigurableApplicationContext run(String provider, String adapter, String model) {
        SpringApplication application = new SpringApplication(SpringAiPropertiesConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        return application.run(
                "--spring.config.location=classpath:/application.yml,classpath:/application-ai.yml",
                "--spring.profiles.active=ai",
                "--spring.main.banner-mode=off",
                "--DEEPSEEK_API_KEY=test-key",
                "--LOANOPS_CHAT_PROVIDER=" + provider,
                "--LOANOPS_CHAT_ADAPTER=" + adapter,
                "--LOANOPS_CHAT_MODEL=" + model);
    }

    private ConfigurableApplicationContext runWithPolicy() {
        SpringApplication application = new SpringApplication(OllamaPropertiesConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        return application.run(
                "--spring.config.location=classpath:/application.yml,classpath:/application-ai.yml,classpath:/application-policy.yml",
                "--spring.profiles.active=ai,policy",
                "--spring.main.banner-mode=off",
                "--LOANOPS_CHAT_PROVIDER=ollama",
                "--LOANOPS_CHAT_ADAPTER=ollama",
                "--LOANOPS_CHAT_MODEL=qwen3:4b",
                "--OLLAMA_BASE_URL=http://127.0.0.1:11435",
                "--OLLAMA_EMBEDDING_MODEL=bge-m3");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            LoanOpsAgentProperties.class,
            DeepSeekChatProperties.class,
            OllamaChatProperties.class
    })
    static class SpringAiPropertiesConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            LoanOpsAgentProperties.class,
            OllamaChatProperties.class,
            OllamaConnectionProperties.class,
            OllamaEmbeddingProperties.class
    })
    static class OllamaPropertiesConfiguration {
    }
}
