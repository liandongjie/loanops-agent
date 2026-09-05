package com.loanops.conversation;

import java.util.List;

public record ConversationSnapshot(
        String conversationId,
        long version,
        int lastMessageSequence,
        List<ConversationHistoryMessage> history,
        String historyHash) {

    public ConversationSnapshot {
        history = List.copyOf(history);
    }
}
