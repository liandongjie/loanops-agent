package com.loanops.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRequestAuditContextTest {

    @Test
    void scopeIsRemovedAfterCloseAndSequenceIsStable() {
        assertThat(AgentRequestAuditContext.current()).isEmpty();

        try (AgentRequestAuditContext.Scope ignored = AgentRequestAuditContext.open("request-a")) {
            AgentRequestAuditContext.State state = AgentRequestAuditContext.current().orElseThrow();
            assertThat(state.requestId()).isEqualTo("request-a");
            assertThat(state.nextSequence()).isEqualTo(1);
            assertThat(state.nextSequence()).isEqualTo(2);
        }

        assertThat(AgentRequestAuditContext.current()).isEmpty();
    }
}