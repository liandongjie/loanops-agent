package com.loanops.conversation;

import java.util.Objects;

public record ConversationHistoryMessage(int sequenceNo, String role, String content) {

    public ConversationHistoryMessage {
        if (!"USER".equals(role) && !"ASSISTANT".equals(role)) {
            throw new IllegalArgumentException("Unsupported conversation role: " + role);
        }
        Objects.requireNonNull(content, "content");
    }
}
