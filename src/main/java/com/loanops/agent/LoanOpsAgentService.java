package com.loanops.agent;

import com.loanops.audit.AgentAuditHandle;
import com.loanops.audit.AgentAuditService;
import com.loanops.audit.AgentRequestAuditContext;
import com.loanops.conversation.ConversationSnapshot;
import com.loanops.conversation.ConversationTurnStore;
import com.loanops.dto.AgentChatResult;
import com.loanops.exception.ConversationConflictException;
import com.loanops.exception.InvalidAgentMessageException;
import com.loanops.observability.AgentMetrics;
import com.loanops.policy.PolicyGroundingContextFactory;
import com.loanops.policy.PolicyRetrievalDecision;
import com.loanops.policy.PolicyRetrievalStatus;
import com.loanops.policy.PolicyRuntimePreparation;
import com.loanops.policy.PolicyRuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Profile("ai")
public class LoanOpsAgentService {

    private static final Logger log = LoggerFactory.getLogger(LoanOpsAgentService.class);
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    private final AgentChatGateway chatGateway;
    private final AgentAuditService auditService;
    private final AgentTurnCompletionService completionService;
    private final ConversationTurnStore turnStore;
    private final AgentMetrics metrics;
    private final PolicyRuntimeService policyRuntimeService;
    private final String provider;
    private final String model;
    private final int maxMessageCharacters;

    public LoanOpsAgentService(
            AgentChatGateway chatGateway,
            AgentAuditService auditService,
            AgentTurnCompletionService completionService,
            ConversationTurnStore turnStore,
            AgentMetrics metrics,
            PolicyRuntimeService policyRuntimeService,
            @Value("${loanops.agent.provider:unknown}") String provider,
            @Value("${loanops.agent.model:unknown}") String model,
            @Value("${loanops.agent.max-message-characters:4000}") int maxMessageCharacters) {
        if (maxMessageCharacters < 1) {
            throw new IllegalArgumentException("Agent message character limit must be positive");
        }
        this.chatGateway = chatGateway;
        this.auditService = auditService;
        this.completionService = completionService;
        this.turnStore = turnStore;
        this.metrics = metrics;
        this.policyRuntimeService = policyRuntimeService;
        this.provider = provider;
        this.model = model;
        this.maxMessageCharacters = maxMessageCharacters;
    }

    public AgentChatResult chat(String message) {
        return chatWithRequestId(UUID.randomUUID().toString(), null, message);
    }

    public AgentChatResult chatWithRequestId(String requestId, String message) {
        return chatWithRequestId(requestId, null, message);
    }

    public AgentChatResult chatWithRequestId(String requestId, String conversationId, String message) {
        long requestStartedNanos = System.nanoTime();
        String auditableMessage = message == null ? "" : message;
        String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);

        try {
            if (message == null || message.isBlank()) {
                AgentAuditHandle auditHandle = beginAudit(
                        requestId, auditableMessage, null, null, requestStartedNanos);
                failBeforeModel(auditHandle, new InvalidAgentMessageException());
            }
            if (message.length() > maxMessageCharacters) {
                AgentAuditHandle auditHandle = beginAudit(
                        requestId, auditableMessage, null, null, requestStartedNanos);
                failBeforeModel(auditHandle, new InvalidAgentMessageException(
                        "message must not exceed " + maxMessageCharacters + " characters"));
            }

            ConversationSnapshot snapshot;
            try {
                snapshot = conversationId == null || conversationId.isBlank()
                        ? turnStore.create()
                        : turnStore.resolve(conversationId);
            } catch (RuntimeException | Error conversationFailure) {
                long durationMs = elapsedMillis(requestStartedNanos);
                metrics.recordRequest("FAILED", durationMs);
                log.info("Agent request completed requestId={} conversationId={} outcome=FAILED provider={} model={} durationMs={} errorType={}",
                        requestId, conversationId, provider, model, durationMs,
                        conversationFailure.getClass().getSimpleName());
                throw conversationFailure;
            }

            String systemPrompt = chatGateway.systemPrompt();
            AgentAuditHandle auditHandle = beginAudit(
                    requestId, auditableMessage, snapshot, systemPrompt, requestStartedNanos);

            log.info("Agent request started requestId={} conversationId={} provider={} model={}",
                    requestId, snapshot.conversationId(), provider, model);
            try (AgentRequestAuditContext.Scope ignored = AgentRequestAuditContext.open(requestId)) {
                PolicyRuntimePreparation policyPreparation;
                try {
                    policyPreparation = policyRuntimeService.prepare(requestId, snapshot, message);
                } catch (RuntimeException | Error policyFailure) {
                    return failRequest(auditHandle, snapshot.conversationId(), policyFailure);
                }

                String answer;
                try {
                    if (policyPreparation.context().decision() == PolicyRetrievalDecision.REQUIRED
                            && policyPreparation.context().retrievalStatus() == PolicyRetrievalStatus.NO_MATCH) {
                        answer = PolicyGroundingContextFactory.NO_MATCH_NOTICE;
                    } else {
                        answer = chatGateway.chat(new AgentChatRequest(
                                snapshot.history(), message, policyPreparation.context()));
                    }
                    answer = policyRuntimeService.validateAndRecord(answer, policyPreparation);
                } catch (RuntimeException | Error modelFailure) {
                    return failRequest(auditHandle, snapshot.conversationId(), modelFailure);
                }

                long durationMs;
                try {
                    durationMs = completionService.complete(auditHandle, snapshot, message, answer);
                } catch (ConversationConflictException conflict) {
                    durationMs = completeFailure(auditHandle, conflict);
                    metrics.recordRequest("FAILED", durationMs);
                    log.info("Agent request completed requestId={} conversationId={} outcome=FAILED provider={} model={} durationMs={} errorType={}",
                            requestId, snapshot.conversationId(), provider, model, durationMs,
                            conflict.getClass().getSimpleName());
                    throw conflict;
                } catch (RuntimeException | Error completionFailure) {
                    metrics.recordAuditWriteFailure("agent_success");
                    durationMs = completeFailure(auditHandle, completionFailure);
                    metrics.recordRequest("FAILED", durationMs);
                    log.error("Agent successful-turn commit failed requestId={} conversationId={} errorType={}",
                            requestId, snapshot.conversationId(), completionFailure.getClass().getSimpleName());
                    throw completionFailure;
                }

                metrics.recordRequest("SUCCESS", durationMs);
                log.info("Agent request completed requestId={} conversationId={} outcome=SUCCESS provider={} model={} durationMs={}",
                        requestId, snapshot.conversationId(), provider, model, durationMs);
                return new AgentChatResult(snapshot.conversationId(), requestId, answer);
            }
        } finally {
            restoreRequestId(previousRequestId);
        }
    }

    private AgentAuditHandle beginAudit(
            String requestId,
            String message,
            ConversationSnapshot snapshot,
            String systemPrompt,
            long requestStartedNanos) {
        try {
            return snapshot == null
                    ? auditService.begin(requestId, message)
                    : auditService.begin(requestId, message, snapshot, systemPrompt);
        } catch (RuntimeException auditFailure) {
            metrics.recordAuditWriteFailure("agent_begin");
            metrics.recordRequest("AUDIT_FAILED", elapsedMillis(requestStartedNanos));
            log.error("Agent audit begin failed requestId={} errorType={}",
                    requestId, auditFailure.getClass().getSimpleName());
            throw auditFailure;
        }
    }

    private void failBeforeModel(AgentAuditHandle auditHandle, InvalidAgentMessageException validationFailure) {
        long durationMs = completeFailure(auditHandle, validationFailure);
        metrics.recordRequest("FAILED", durationMs);
        log.info("Agent request completed requestId={} outcome=FAILED provider={} model={} durationMs={} errorType={}",
                auditHandle.requestId(), provider, model, durationMs,
                validationFailure.getClass().getSimpleName());
        throw validationFailure;
    }

    private AgentChatResult failRequest(AgentAuditHandle auditHandle, String conversationId, Throwable failure) {
        long durationMs = completeFailure(auditHandle, failure);
        metrics.recordRequest("FAILED", durationMs);
        log.info("Agent request completed requestId={} conversationId={} outcome=FAILED provider={} model={} durationMs={} errorType={}",
                auditHandle.requestId(), conversationId, provider, model, durationMs,
                failure.getClass().getSimpleName());
        if (failure instanceof RuntimeException runtimeException) throw runtimeException;
        throw (Error) failure;
    }

    private long completeFailure(AgentAuditHandle auditHandle, Throwable failure) {
        long durationMs = auditService.elapsedMillis(auditHandle);
        try {
            return auditService.completeFailure(auditHandle, failure);
        } catch (RuntimeException auditFailure) {
            metrics.recordAuditWriteFailure("agent_failure");
            failure.addSuppressed(auditFailure);
            log.error("Agent failure audit completion failed requestId={} errorType={}",
                    auditHandle.requestId(), auditFailure.getClass().getSimpleName());
            return durationMs;
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static void restoreRequestId(String previousRequestId) {
        if (previousRequestId == null) {
            MDC.remove(REQUEST_ID_MDC_KEY);
        } else {
            MDC.put(REQUEST_ID_MDC_KEY, previousRequestId);
        }
    }
}
