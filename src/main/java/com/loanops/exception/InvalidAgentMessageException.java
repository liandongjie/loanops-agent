package com.loanops.exception;

public class InvalidAgentMessageException extends RuntimeException {
    public InvalidAgentMessageException() {
        super("message must not be blank");
    }

    public InvalidAgentMessageException(String message) {
        super(message);
    }
}
