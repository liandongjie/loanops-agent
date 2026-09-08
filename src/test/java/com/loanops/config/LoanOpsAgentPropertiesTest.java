package com.loanops.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class LoanOpsAgentPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @ParameterizedTest
    @CsvSource({
            "deepseek, deepseek",
            "ollama, ollama",
            "glm, openai"
    })
    void acceptsFrozenProviderAdapterMappings(String provider, String adapter) {
        contextRunner.withPropertyValues(
                        "loanops.agent.provider=" + provider,
                        "loanops.agent.adapter=" + adapter,
                        "loanops.agent.model=dummy-model")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LoanOpsAgentProperties.class))
                            .isEqualTo(new LoanOpsAgentProperties(provider, adapter, "dummy-model"));
                });
    }

    @ParameterizedTest
    @CsvSource({
            "glm, glm, glm -> openai",
            "glm, deepseek, glm -> openai",
            "deepseek, openai, deepseek -> deepseek"
    })
    void rejectsInvalidProviderAdapterMappings(String provider, String adapter, String expectedMapping) {
        contextRunner.withPropertyValues(
                        "loanops.agent.provider=" + provider,
                        "loanops.agent.adapter=" + adapter,
                        "loanops.agent.model=dummy-model")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("provider='" + provider + "'")
                        .hasMessageContaining("adapter='" + adapter + "'")
                        .hasMessageContaining("expected mapping '" + expectedMapping + "'"));
    }

    @Test
    void rejectsUnknownProviderAndAdapter() {
        assertInvalid("unknown", "deepseek", "Unknown loanops.agent.provider");
        assertInvalid("deepseek", "unknown", "expected mapping 'deepseek -> deepseek'");
    }

    @ParameterizedTest
    @CsvSource({
            "provider, ' ', loanops.agent.provider must not be blank",
            "adapter, ' ', loanops.agent.adapter must not be blank",
            "model, ' ', loanops.agent.model must not be blank"
    })
    void rejectsBlankIdentityFields(String field, String value, String expectedMessage) {
        contextRunner.withPropertyValues(
                        "loanops.agent.provider=deepseek",
                        "loanops.agent.adapter=deepseek",
                        "loanops.agent.model=dummy-model",
                        "loanops.agent." + field + "=" + value)
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining(expectedMessage));
    }

    @Test
    void rejectsModelLongerThanAuditColumn() {
        contextRunner.withPropertyValues(
                        "loanops.agent.provider=deepseek",
                        "loanops.agent.adapter=deepseek",
                        "loanops.agent.model=" + "m".repeat(129))
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("loanops.agent.model must not exceed 128 characters"));
    }

    private void assertInvalid(String provider, String adapter, String expectedMessage) {
        contextRunner.withPropertyValues(
                        "loanops.agent.provider=" + provider,
                        "loanops.agent.adapter=" + adapter,
                        "loanops.agent.model=dummy-model")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining(expectedMessage));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoanOpsAgentProperties.class)
    static class PropertiesConfiguration {
    }
}
