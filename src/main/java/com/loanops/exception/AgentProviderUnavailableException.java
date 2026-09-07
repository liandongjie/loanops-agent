package com.loanops.exception;

public class AgentProviderUnavailableException extends RuntimeException {

    public AgentProviderUnavailableException(Throwable cause) {
        super("Agent provider is temporarily unavailable", cause);
    }
}
