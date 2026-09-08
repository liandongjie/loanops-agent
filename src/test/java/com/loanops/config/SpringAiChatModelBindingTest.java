package com.loanops.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
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

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            LoanOpsAgentProperties.class,
            DeepSeekChatProperties.class,
            OllamaChatProperties.class
    })
    static class SpringAiPropertiesConfiguration {
    }
}
