package com.loanops.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingProperties;
import org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiChatProperties;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiChatModelBindingTest {

    @Test
    void bindsRuntimeModelToDeepSeekChatOptions() {
        try (ConfigurableApplicationContext context = runProperties("deepseek", "deepseek", "test-deepseek-model")) {
            assertThat(context.getBean(DeepSeekChatProperties.class).getOptions().getModel())
                    .isEqualTo("test-deepseek-model");
        }
    }

    @Test
    void bindsRuntimeModelToOllamaChatOptions() {
        try (ConfigurableApplicationContext context = runProperties("ollama", "ollama", "test-ollama-model")) {
            assertThat(context.getBean(OllamaChatProperties.class).getOptions().getModel())
                    .isEqualTo("test-ollama-model");
        }
    }

    @Test
    void bindsRuntimeModelToZhiPuAiChatOptions() {
        try (ConfigurableApplicationContext context = runProperties("glm", "zhipuai", "glm-5.2")) {
            assertThat(context.getBean(ZhiPuAiChatProperties.class).getOptions().getModel())
                    .isEqualTo("glm-5.2");
        }
    }

    @Test
    void selectsZhiPuAiChatModelWithoutDeepSeekSecret() {
        try (ConfigurableApplicationContext context = runSelectedChatModel(
                "ai", "glm", "zhipuai", "glm-5.2",
                "--GLM_API_KEY=test-glm-key", "--DEEPSEEK_API_KEY=")) {
            assertSingleChatModel(context, ZhiPuAiChatModel.class);
        }
    }

    @Test
    void selectsOllamaChatModelWithoutGlmSecret() {
        try (ConfigurableApplicationContext context = runSelectedChatModel(
                "ai", "ollama", "ollama", "qwen3:4b",
                "--GLM_API_KEY=", "--DEEPSEEK_API_KEY=")) {
            assertSingleChatModel(context, OllamaChatModel.class);
        }
    }

    @Test
    void selectsDeepSeekChatModelWithoutGlmSecret() {
        try (ConfigurableApplicationContext context = runSelectedChatModel(
                "ai", "deepseek", "deepseek", "deepseek-chat",
                "--GLM_API_KEY=", "--DEEPSEEK_API_KEY=test-deepseek-key")) {
            assertSingleChatModel(context, DeepSeekChatModel.class);
        }
    }

    @Test
    void keepsOllamaChatAndEmbeddingConfigurationIndependent() {
        try (ConfigurableApplicationContext context = runOllamaPropertiesWithPolicy()) {
            assertThat(context.getBean(OllamaChatProperties.class).getOptions().getModel())
                    .isEqualTo("qwen3:4b");
            assertThat(context.getBean(OllamaEmbeddingProperties.class).getModel())
                    .isEqualTo("bge-m3");
            assertThat(context.getBean(OllamaConnectionProperties.class).getBaseUrl())
                    .isEqualTo("http://127.0.0.1:11435");
        }
    }

    @Test
    void keepsGlmChatAndOllamaEmbeddingConfigurationIndependent() {
        try (ConfigurableApplicationContext context = runSelectedChatModel(
                "ai,policy", "glm", "zhipuai", "glm-5.2",
                "--GLM_API_KEY=test-glm-key", "--DEEPSEEK_API_KEY=",
                "--OLLAMA_BASE_URL=http://127.0.0.1:11435", "--OLLAMA_EMBEDDING_MODEL=bge-m3")) {
            assertSingleChatModel(context, ZhiPuAiChatModel.class);
            assertThat(context.getBean(ZhiPuAiChatProperties.class).getOptions().getModel())
                    .isEqualTo("glm-5.2");
            assertThat(context.getBean(OllamaEmbeddingProperties.class).getModel())
                    .isEqualTo("bge-m3");
            assertThat(context.getEnvironment().getProperty("spring.ai.model.embedding"))
                    .isEqualTo("ollama");
        }
    }

    private ConfigurableApplicationContext runProperties(String provider, String adapter, String model) {
        SpringApplication application = new SpringApplication(SpringAiPropertiesConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        return application.run(
                "--spring.config.location=classpath:/application.yml,classpath:/application-ai.yml",
                "--spring.config.import=optional:file:./target/a3-test-missing.env[.properties]",
                "--spring.profiles.active=ai",
                "--spring.main.banner-mode=off",
                "--LOANOPS_CHAT_PROVIDER=" + provider,
                "--LOANOPS_CHAT_ADAPTER=" + adapter,
                "--LOANOPS_CHAT_MODEL=" + model);
    }

    private ConfigurableApplicationContext runOllamaPropertiesWithPolicy() {
        SpringApplication application = new SpringApplication(OllamaPropertiesConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        return application.run(
                "--spring.config.location=classpath:/application.yml,classpath:/application-ai.yml,classpath:/application-policy.yml",
                "--spring.config.import=optional:file:./target/a3-test-missing.env[.properties]",
                "--spring.profiles.active=ai,policy",
                "--spring.main.banner-mode=off",
                "--LOANOPS_CHAT_PROVIDER=ollama",
                "--LOANOPS_CHAT_ADAPTER=ollama",
                "--LOANOPS_CHAT_MODEL=qwen3:4b",
                "--OLLAMA_BASE_URL=http://127.0.0.1:11435",
                "--OLLAMA_EMBEDDING_MODEL=bge-m3");
    }

    private ConfigurableApplicationContext runSelectedChatModel(
            String profiles, String provider, String adapter, String model, String... secretAndPolicyOverrides) {
        SpringApplication application = new SpringApplication(SelectedChatModelConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        List<String> arguments = new ArrayList<>(List.of(
                "--spring.config.location=classpath:/application.yml,classpath:/application-ai.yml,classpath:/application-policy.yml",
                "--spring.config.import=optional:file:./target/a3-test-missing.env[.properties]",
                "--spring.profiles.active=" + profiles,
                "--spring.main.banner-mode=off",
                "--LOANOPS_CHAT_PROVIDER=" + provider,
                "--LOANOPS_CHAT_ADAPTER=" + adapter,
                "--LOANOPS_CHAT_MODEL=" + model));
        arguments.addAll(List.of(secretAndPolicyOverrides));
        return application.run(arguments.toArray(String[]::new));
    }

    private void assertSingleChatModel(
            ConfigurableApplicationContext context, Class<? extends ChatModel> expectedType) {
        assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
        assertThat(context.getBean(ChatModel.class)).isInstanceOf(expectedType);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            LoanOpsAgentProperties.class,
            DeepSeekChatProperties.class,
            OllamaChatProperties.class,
            ZhiPuAiChatProperties.class
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

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableConfigurationProperties(LoanOpsAgentProperties.class)
    static class SelectedChatModelConfiguration {
    }
}
