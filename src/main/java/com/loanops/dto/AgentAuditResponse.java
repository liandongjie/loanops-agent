package com.loanops.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AgentAuditResponse(
        String requestId,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String status,
        Long durationMs,
        LocalDate businessDate,
        String provider,
        String model,
        Integer messageLength,
        Integer answerLength,
        String messageHash,
        String answerHash,
        String messageText,
        String answerText,
        String errorType,
        String conversationId,
        Integer historyFromSequence,
        Integer historyToSequence,
        String historyHash,
        String systemPromptHash,
        List<AgentToolAuditResponse> tools) {
}
