package com.loanops.policy;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
@Profile("local-policy-bootstrap")
public class LocalPolicyBootstrapRunner implements ApplicationRunner {

    private static final String CORPUS = "classpath:policy/demo-policy-corpus.json";

    private final LocalPolicyBootstrapService bootstrapService;
    private final ResourceLoader resourceLoader;
    private final ConfigurableApplicationContext applicationContext;

    public LocalPolicyBootstrapRunner(LocalPolicyBootstrapService bootstrapService,
                                      ResourceLoader resourceLoader,
                                      ConfigurableApplicationContext applicationContext) {
        this.bootstrapService = bootstrapService;
        this.resourceLoader = resourceLoader;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalPolicyBootstrapService.BootstrapResult result =
                bootstrapService.bootstrap(resourceLoader.getResource(CORPUS));
        System.out.printf("LOCAL_POLICY_BOOTSTRAP_SUCCESS documents=%d versions=%d chunks=%d indexed=%d%n",
                result.documentCount(), result.versionCount(), result.chunkCount(), result.indexedCount());
        SpringApplication.exit(applicationContext);
    }
}
