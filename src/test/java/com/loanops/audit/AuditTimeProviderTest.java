package com.loanops.audit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AuditTimeProviderTest {

    @Test
    void technicalTimeAndDurationUseIndependentInjectableSources() {
        AtomicLong nanos = new AtomicLong(1_000_000L);
        AuditTimeProvider provider = new AuditTimeProvider(
                Clock.fixed(Instant.parse("2026-08-23T12:34:56Z"), ZoneOffset.UTC),
                () -> nanos.getAndAdd(1_000_000L));

        long started = provider.startNanos();
        assertThat(provider.nowUtc()).hasToString("2026-08-23T12:34:56");
        assertThat(provider.elapsedMillis(started)).isEqualTo(1L);
    }
}
