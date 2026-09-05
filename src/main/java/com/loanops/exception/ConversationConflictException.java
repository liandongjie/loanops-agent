package com.loanops.exception;

public class ConversationConflictException extends RuntimeException {

    public ConversationConflictException(
            String conversationId,
            long expectedVersion,
            int expectedLastMessageSequence) {
        super("Conversation changed before turn completion: " + conversationId
                + " (expected version=" + expectedVersion
                + ", lastMessageSequence=" + expectedLastMessageSequence + ")");
    }
}
