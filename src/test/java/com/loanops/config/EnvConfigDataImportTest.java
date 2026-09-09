package com.loanops.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EnvConfigDataImportTest {

    @TempDir
    Path tempDirectory;

    @Test
    void loadsExtensionlessPropertiesFileAndAllowsCommandLineOverride() throws IOException {
        Path envFile = tempDirectory.resolve(".env");
        Files.writeString(envFile, "LOANOPS_CHAT_MODEL=file-model\n");
        Path applicationFile = tempDirectory.resolve("application.properties");
        Files.writeString(applicationFile, """
                spring.config.import=%s[.properties]
                loanops.agent.provider=${LOANOPS_CHAT_PROVIDER:deepseek}
                loanops.agent.adapter=${LOANOPS_CHAT_ADAPTER:deepseek}
                loanops.agent.model=${LOANOPS_CHAT_MODEL:deepseek-chat}
                """.formatted(envFile.toUri()));

        try (ConfigurableApplicationContext context = run(applicationFile)) {
            assertThat(context.getBean(LoanOpsAgentProperties.class).model()).isEqualTo("file-model");
        }
        try (ConfigurableApplicationContext context = run(
                applicationFile, "--LOANOPS_CHAT_MODEL=command-line-model")) {
            assertThat(context.getBean(LoanOpsAgentProperties.class).model())
                    .isEqualTo("command-line-model");
        }
    }

    private ConfigurableApplicationContext run(Path applicationFile, String... additionalArguments) {
        SpringApplication application = new SpringApplication(ConfigDataConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        String[] arguments = new String[additionalArguments.length + 2];
        arguments[0] = "--spring.config.location=" + applicationFile.toUri();
        arguments[1] = "--spring.main.banner-mode=off";
        System.arraycopy(additionalArguments, 0, arguments, 2, additionalArguments.length);
        return application.run(arguments);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoanOpsAgentProperties.class)
    static class ConfigDataConfiguration {
    }
}
