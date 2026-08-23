package com.loanops.audit;

public record AgentAuditHandle(String requestId, long startedNanos) {
}