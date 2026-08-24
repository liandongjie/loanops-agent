package com.loanops.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("agent_audit_log")
public class AgentAuditLogEntity {

    @TableId(type = IdType.INPUT)
    private String requestId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String status;
    private Long durationMs;
    private LocalDate businessDate;
    private String provider;
    private String model;
    private Integer messageLength;
    private Integer answerLength;
    private String messageHash;
    private String answerHash;
    private String messageText;
    private String answerText;
    private String errorType;
    private String conversationId;
    private Integer historyFromSequence;
    private Integer historyToSequence;
    private String historyHash;
    private String systemPromptHash;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getMessageLength() { return messageLength; }
    public void setMessageLength(Integer messageLength) { this.messageLength = messageLength; }
    public Integer getAnswerLength() { return answerLength; }
    public void setAnswerLength(Integer answerLength) { this.answerLength = answerLength; }
    public String getMessageHash() { return messageHash; }
    public void setMessageHash(String messageHash) { this.messageHash = messageHash; }
    public String getAnswerHash() { return answerHash; }
    public void setAnswerHash(String answerHash) { this.answerHash = answerHash; }
    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Integer getHistoryFromSequence() { return historyFromSequence; }
    public void setHistoryFromSequence(Integer historyFromSequence) { this.historyFromSequence = historyFromSequence; }
    public Integer getHistoryToSequence() { return historyToSequence; }
    public void setHistoryToSequence(Integer historyToSequence) { this.historyToSequence = historyToSequence; }
    public String getHistoryHash() { return historyHash; }
    public void setHistoryHash(String historyHash) { this.historyHash = historyHash; }
    public String getSystemPromptHash() { return systemPromptHash; }
    public void setSystemPromptHash(String systemPromptHash) { this.systemPromptHash = systemPromptHash; }
}
