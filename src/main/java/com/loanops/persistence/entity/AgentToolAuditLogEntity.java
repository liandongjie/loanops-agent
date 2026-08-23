package com.loanops.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_tool_audit_log")
public class AgentToolAuditLogEntity {

    // The database primary key is composite: (request_id, sequence_no).
    // Intentionally no @TableId: MyBatis-Plus does not model composite ids here,
    // and this entity is accessed only with explicit requestId + sequenceNo predicates.
    private String requestId;
    private Integer sequenceNo;
    private String toolName;
    private String loanNo;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String status;
    private Long durationMs;
    private String errorType;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Integer getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Integer sequenceNo) { this.sequenceNo = sequenceNo; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getLoanNo() { return loanNo; }
    public void setLoanNo(String loanNo) { this.loanNo = loanNo; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
}
