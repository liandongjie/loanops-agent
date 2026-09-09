package com.loanops;

import com.loanops.config.LoanOpsAgentProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LoanOpsAgentProperties.class)
@MapperScan("com.loanops.persistence.mapper")
public class LoanOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanOpsApplication.class, args);
    }
}
