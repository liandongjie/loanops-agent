package com.loanops.dto;

import java.time.LocalDateTime;

public record AgentToolAuditResponse(
        Integer sequenceNo,
        String toolName,
        String loanNo,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String status,
        Long durationMs,
        String errorType) {
}
