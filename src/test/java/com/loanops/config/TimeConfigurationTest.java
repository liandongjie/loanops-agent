package com.loanops.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TimeConfigurationTest {

    private final TimeConfiguration configuration = new TimeConfiguration();

    @Test
    void businessClock_usesConfiguredBusinessDateAndZone() {
        Clock clock = configuration.businessClock("2026-08-23", "Asia/Shanghai");

        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 8, 23));
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
    }

    @Test
    void businessClock_usesConfiguredZoneWhenDateIsNotPinned() {
        Clock clock = configuration.businessClock("", "UTC");

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("UTC"));
    }
}
