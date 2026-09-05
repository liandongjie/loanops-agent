package com.loanops.conversation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationHistoryFingerprintTest {

    @Test
    void sameOrderedHistoryAlwaysProducesSameHash() {
        List<ConversationHistoryMessage> history = List.of(
                new ConversationHistoryMessage(1, "USER", "a|b\nsecond line"),
                new ConversationHistoryMessage(2, "ASSISTANT", "answer\n|with separators"));

        assertThat(ConversationHistoryFingerprint.sha256(history))
                .isEqualTo(ConversationHistoryFingerprint.sha256(List.copyOf(history)))
                .hasSize(64);
    }

    @Test
    void contentRoleSequenceAndOrderEachAffectHash() {
        List<ConversationHistoryMessage> original = List.of(
                new ConversationHistoryMessage(1, "USER", "question"),
                new ConversationHistoryMessage(2, "ASSISTANT", "answer"));
        String originalHash = ConversationHistoryFingerprint.sha256(original);

        assertThat(ConversationHistoryFingerprint.sha256(List.of(
                new ConversationHistoryMessage(1, "USER", "changed"),
                new ConversationHistoryMessage(2, "ASSISTANT", "answer")))).isNotEqualTo(originalHash);
        assertThat(ConversationHistoryFingerprint.sha256(List.of(
                new ConversationHistoryMessage(1, "ASSISTANT", "question"),
                new ConversationHistoryMessage(2, "ASSISTANT", "answer")))).isNotEqualTo(originalHash);
        assertThat(ConversationHistoryFingerprint.sha256(List.of(
                new ConversationHistoryMessage(3, "USER", "question"),
                new ConversationHistoryMessage(2, "ASSISTANT", "answer")))).isNotEqualTo(originalHash);
        assertThat(ConversationHistoryFingerprint.sha256(List.of(
                original.get(1), original.get(0)))).isNotEqualTo(originalHash);
    }
}
