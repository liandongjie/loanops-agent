package com.loanops.exception;

public class AgentAuditNotFoundException extends RuntimeException {

    public AgentAuditNotFoundException(String requestId) {
        super("Agent audit not found: " + requestId);
    }
}
