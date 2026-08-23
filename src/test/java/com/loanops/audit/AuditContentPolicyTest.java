package com.loanops.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditContentPolicyTest {

    @Test
    void defaultStylePolicyStoresFingerprintButNotRawContent() {
        AuditContentSnapshot snapshot = new AuditContentPolicy(false).snapshot("LN-10002 为什么逾期？");

        assertThat(snapshot.length()).isEqualTo("LN-10002 为什么逾期？".length());
        assertThat(snapshot.sha256()).hasSize(64);
        assertThat(snapshot.content()).isNull();
    }

    @Test
    void explicitIncludeContentStoresRawContent() {
        AuditContentSnapshot snapshot = new AuditContentPolicy(true).snapshot("LN-10002 为什么逾期？");

        assertThat(snapshot.sha256()).hasSize(64);
        assertThat(snapshot.content()).isEqualTo("LN-10002 为什么逾期？");
    }
}