package com.loanops.audit;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.LongSupplier;

@Component
public class AuditTimeProvider {

    private final Clock clock;
    private final LongSupplier nanoTime;

    public AuditTimeProvider() {
        this(Clock.systemUTC(), System::nanoTime);
    }

    AuditTimeProvider(Clock clock, LongSupplier nanoTime) {
        this.clock = Objects.requireNonNull(clock);
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    public LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public long startNanos() {
        return nanoTime.getAsLong();
    }

    public long elapsedMillis(long startedNanos) {
        return Math.max(0L, (nanoTime.getAsLong() - startedNanos) / 1_000_000L);
    }
}