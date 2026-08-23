package com.loanops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Configuration
public class TimeConfiguration {

    @Bean
    Clock businessClock(
            @Value("${loanops.business-date:}") String configuredBusinessDate,
            @Value("${loanops.business-zone:}") String configuredBusinessZone) {
        ZoneId zone = configuredBusinessZone == null || configuredBusinessZone.isBlank()
                ? ZoneId.systemDefault()
                : ZoneId.of(configuredBusinessZone.trim());

        if (configuredBusinessDate == null || configuredBusinessDate.isBlank()) {
            return Clock.system(zone);
        }

        LocalDate businessDate = LocalDate.parse(configuredBusinessDate.trim());
        return Clock.fixed(businessDate.atStartOfDay(zone).toInstant(), zone);
    }
}
