package com.loanops.audit;

public record ToolAuditHandle(String requestId, int sequenceNo, long startedNanos) {
}