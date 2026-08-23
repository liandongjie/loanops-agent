package com.loanops;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.loanops.persistence.mapper")
public class LoanOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanOpsApplication.class, args);
    }
}
