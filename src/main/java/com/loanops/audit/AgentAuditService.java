package com.loanops.audit;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.loanops.conversation.ConversationHistoryMessage;
import com.loanops.conversation.ConversationSnapshot;
import com.loanops.dto.AgentAuditResponse;
import com.loanops.dto.AgentToolAuditResponse;
import com.loanops.exception.AgentAuditNotFoundException;
import com.loanops.persistence.entity.AgentAuditLogEntity;
import com.loanops.persistence.entity.AgentToolAuditLogEntity;
import com.loanops.persistence.mapper.AgentAuditLogMapper;
import com.loanops.persistence.mapper.AgentToolAuditLogMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class AgentAuditService {

    private final AgentAuditLogMapper auditMapper;
    private final AgentToolAuditLogMapper toolAuditMapper;
    private final AuditContentPolicy contentPolicy;
    private final AuditTimeProvider timeProvider;
    private final Clock businessClock;
    private final String provider;
    private final String model;

    public AgentAuditService(
            AgentAuditLogMapper auditMapper,
            AgentToolAuditLogMapper toolAuditMapper,
            AuditContentPolicy contentPolicy,
            AuditTimeProvider timeProvider,
            Clock businessClock,
            @Value("${loanops.agent.provider:unknown}") String provider,
            @Value("${loanops.agent.model:unknown}") String model) {
        this.auditMapper = auditMapper;
        this.toolAuditMapper = toolAuditMapper;
        this.contentPolicy = contentPolicy;
        this.timeProvider = timeProvider;
        this.businessClock = businessClock;
        this.provider = normalizeMetadata(provider, 32);
        this.model = normalizeMetadata(model, 128);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentAuditHandle begin(String requestId, String message) {
        return insertStarted(requestId, message, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentAuditHandle begin(
            String requestId,
            String message,
            ConversationSnapshot conversation,
            String systemPrompt) {
        return insertStarted(
                requestId,
                message,
                Objects.requireNonNull(conversation, "conversation"),
                Objects.requireNonNull(systemPrompt, "systemPrompt"));
    }

    @Transactional
    public long completeSuccess(AgentAuditHandle handle, String answer) {
        AuditContentSnapshot snapshot = contentPolicy.snapshot(answer);
        long durationMs = timeProvider.elapsedMillis(handle.startedNanos());
        LambdaUpdateWrapper<AgentAuditLogEntity> update = new LambdaUpdateWrapper<AgentAuditLogEntity>()
                .eq(AgentAuditLogEntity::getRequestId, handle.requestId())
                .eq(AgentAuditLogEntity::getStatus, "STARTED")
                .set(AgentAuditLogEntity::getCompletedAt, timeProvider.nowUtc())
                .set(AgentAuditLogEntity::getStatus, "SUCCESS")
                .set(AgentAuditLogEntity::getDurationMs, durationMs)
                .set(AgentAuditLogEntity::getAnswerLength, snapshot.length())
                .set(AgentAuditLogEntity::getAnswerHash, snapshot.sha256())
                .set(AgentAuditLogEntity::getAnswerText, snapshot.content())
                .set(AgentAuditLogEntity::getErrorType, null);
        requireSingleUpdate(auditMapper.update(null, update), handle.requestId());
        return durationMs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long completeFailure(AgentAuditHandle handle, Throwable failure) {
        long durationMs = timeProvider.elapsedMillis(handle.startedNanos());
        LambdaUpdateWrapper<AgentAuditLogEntity> update = new LambdaUpdateWrapper<AgentAuditLogEntity>()
                .eq(AgentAuditLogEntity::getRequestId, handle.requestId())
                .eq(AgentAuditLogEntity::getStatus, "STARTED")
                .set(AgentAuditLogEntity::getCompletedAt, timeProvider.nowUtc())
                .set(AgentAuditLogEntity::getStatus, "FAILED")
                .set(AgentAuditLogEntity::getDurationMs, durationMs)
                .set(AgentAuditLogEntity::getErrorType, errorType(failure));
        requireSingleUpdate(auditMapper.update(null, update), handle.requestId());
        return durationMs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ToolAuditHandle beginTool(String requestId, int sequenceNo, String toolName, String loanNo) {
        AgentToolAuditLogEntity entity = new AgentToolAuditLogEntity();
        entity.setRequestId(requestId);
        entity.setSequenceNo(sequenceNo);
        entity.setToolName(toolName);
        entity.setLoanNo(loanNo);
        entity.setStartedAt(timeProvider.nowUtc());
        entity.setStatus("STARTED");
        if (toolAuditMapper.insert(entity) != 1) {
            throw new IllegalStateException("agent tool audit insert affected no rows");
        }
        return new ToolAuditHandle(requestId, sequenceNo, timeProvider.startNanos());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long completeToolSuccess(ToolAuditHandle handle) {
        return completeTool(handle, "SUCCESS", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long completeToolFailure(ToolAuditHandle handle, Throwable failure) {
        return completeTool(handle, "FAILED", failure);
    }

    @Transactional(readOnly = true)
    public AgentAuditResponse get(String requestId) {
        AgentAuditLogEntity audit = auditMapper.selectById(requestId);
        if (audit == null) {
            throw new AgentAuditNotFoundException(requestId);
        }
        List<AgentToolAuditResponse> tools = toolAuditMapper.selectByRequestId(requestId)
                .stream()
                .map(this::toToolResponse)
                .toList();
        return new AgentAuditResponse(
                audit.getRequestId(),
                audit.getStartedAt(),
                audit.getCompletedAt(),
                audit.getStatus(),
                audit.getDurationMs(),
                audit.getBusinessDate(),
                audit.getProvider(),
                audit.getModel(),
                audit.getMessageLength(),
                audit.getAnswerLength(),
                audit.getMessageHash(),
                audit.getAnswerHash(),
                audit.getMessageText(),
                audit.getAnswerText(),
                audit.getErrorType(),
                audit.getConversationId(),
                audit.getHistoryFromSequence(),
                audit.getHistoryToSequence(),
                audit.getHistoryHash(),
                audit.getSystemPromptHash(),
                tools);
    }

    public long elapsedMillis(AgentAuditHandle handle) {
        return timeProvider.elapsedMillis(handle.startedNanos());
    }

    public long elapsedMillis(ToolAuditHandle handle) {
        return timeProvider.elapsedMillis(handle.startedNanos());
    }

    private AgentAuditHandle insertStarted(
            String requestId,
            String message,
            ConversationSnapshot conversation,
            String systemPrompt) {
        long startedNanos = timeProvider.startNanos();
        AuditContentSnapshot snapshot = contentPolicy.snapshot(message);
        AgentAuditLogEntity entity = new AgentAuditLogEntity();
        entity.setRequestId(requestId);
        entity.setStartedAt(timeProvider.nowUtc());
        entity.setStatus("STARTED");
        entity.setBusinessDate(LocalDate.now(businessClock));
        entity.setProvider(provider);
        entity.setModel(model);
        entity.setMessageLength(snapshot.length());
        entity.setMessageHash(snapshot.sha256());
        entity.setMessageText(snapshot.content());
        if (conversation != null) {
            List<ConversationHistoryMessage> history = conversation.history();
            entity.setConversationId(conversation.conversationId());
            entity.setHistoryFromSequence(history.isEmpty() ? null : history.getFirst().sequenceNo());
            entity.setHistoryToSequence(history.isEmpty() ? null : history.getLast().sequenceNo());
            entity.setHistoryHash(conversation.historyHash());
            entity.setSystemPromptHash(contentPolicy.snapshot(systemPrompt).sha256());
        }
        if (auditMapper.insert(entity) != 1) {
            throw new IllegalStateException("agent audit insert affected no rows");
        }
        return new AgentAuditHandle(requestId, startedNanos);
    }

    private long completeTool(ToolAuditHandle handle, String status, Throwable failure) {
        long durationMs = timeProvider.elapsedMillis(handle.startedNanos());
        String failureType = failure == null ? null : errorType(failure);
        if (toolAuditMapper.complete(
                handle.requestId(),
                handle.sequenceNo(),
                timeProvider.nowUtc(),
                status,
                durationMs,
                failureType) != 1) {
            throw new IllegalStateException("agent tool audit update affected no rows: "
                    + handle.requestId() + "/" + handle.sequenceNo());
        }
        return durationMs;
    }

    private AgentToolAuditResponse toToolResponse(AgentToolAuditLogEntity entity) {
        return new AgentToolAuditResponse(
                entity.getSequenceNo(),
                entity.getToolName(),
                entity.getLoanNo(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getStatus(),
                entity.getDurationMs(),
                entity.getErrorType());
    }

    private static void requireSingleUpdate(int count, String requestId) {
        if (count != 1) {
            throw new IllegalStateException("agent audit is not in STARTED state: " + requestId);
        }
    }

    private static String normalizeMetadata(String value, int maxLength) {
        String normalized = value == null || value.isBlank() ? "unknown" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static String errorType(Throwable failure) {
        if (failure == null) {
            return "UNKNOWN";
        }
        String type = failure.getClass().getSimpleName();
        String resolved = type.isBlank() ? failure.getClass().getName() : type;
        return resolved.length() <= 128 ? resolved : resolved.substring(0, 128);
    }
}
