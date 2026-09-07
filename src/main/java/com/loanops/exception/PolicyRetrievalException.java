package com.loanops.exception;

public class PolicyRetrievalException extends RuntimeException {
    public PolicyRetrievalException(Throwable cause) {
        super("Required policy retrieval failed", cause);
    }
}
