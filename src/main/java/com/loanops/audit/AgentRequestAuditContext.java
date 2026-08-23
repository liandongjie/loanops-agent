package com.loanops.audit;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class AgentRequestAuditContext {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private AgentRequestAuditContext() {
    }

    public static Scope open(String requestId) {
        State previous = CURRENT.get();
        State current = new State(requestId);
        CURRENT.set(current);
        return new Scope(previous, current);
    }

    public static Optional<State> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static final class State {
        private final String requestId;
        private final AtomicInteger sequence = new AtomicInteger();

        private State(String requestId) {
            this.requestId = requestId;
        }

        public String requestId() {
            return requestId;
        }

        public int nextSequence() {
            return sequence.incrementAndGet();
        }
    }

    public static final class Scope implements AutoCloseable {
        private final State previous;
        private final State opened;
        private boolean closed;

        private Scope(State previous, State opened) {
            this.previous = previous;
            this.opened = opened;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (CURRENT.get() == opened) {
                if (previous == null) {
                    CURRENT.remove();
                } else {
                    CURRENT.set(previous);
                }
            }
            closed = true;
        }
    }
}