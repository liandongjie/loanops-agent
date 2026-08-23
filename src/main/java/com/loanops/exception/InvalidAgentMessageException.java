package com.loanops.exception;

public class InvalidAgentMessageException extends RuntimeException {
    public InvalidAgentMessageException() {
        super("message must not be blank");
    }
}