package com.loanops.agent;

import com.loanops.audit.AgentAuditHandle;
import com.loanops.audit.AgentAuditService;
import com.loanops.audit.AgentRequestAuditContext;
import com.loanops.dto.AgentChatResult;
import com.loanops.exception.InvalidAgentMessageException;
import com.loanops.observability.AgentMetrics;
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
    private final AgentMetrics metrics;
    private final String provider;
    private final String model;

    public LoanOpsAgentService(
            AgentChatGateway chatGateway,
            AgentAuditService auditService,
            AgentMetrics metrics,
            @Value("${loanops.agent.provider:unknown}") String provider,
            @Value("${loanops.agent.model:unknown}") String model) {
        this.chatGateway = chatGateway;
        this.auditService = auditService;
        this.metrics = metrics;
        this.provider = provider;
        this.model = model;
    }

    public AgentChatResult chat(String message) {
        return chatWithRequestId(UUID.randomUUID().toString(), message);
    }

    public AgentChatResult chatWithRequestId(String requestId, String message) {
        long requestStartedNanos = System.nanoTime();
        String auditableMessage = message == null ? "" : message;
        String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);

        AgentAuditHandle auditHandle;
        try {
            // Fail closed: the remote Agent is never invoked unless STARTED is committed first.
            auditHandle = auditService.begin(requestId, auditableMessage);
        } catch (RuntimeException auditFailure) {
            metrics.recordAuditWriteFailure("agent_begin");
            metrics.recordRequest("AUDIT_FAILED", elapsedMillis(requestStartedNanos));
            log.error("Agent audit begin failed requestId={} errorType={}",
                    requestId, auditFailure.getClass().getSimpleName());
            restoreRequestId(previousRequestId);
            throw auditFailure;
        }

        if (message == null || message.isBlank()) {
            InvalidAgentMessageException validationFailure = new InvalidAgentMessageException();
            return failBeforeModel(auditHandle, validationFailure, previousRequestId);
        }

        log.info("Agent request started requestId={} provider={} model={}", requestId, provider, model);
        try (AgentRequestAuditContext.Scope ignored = AgentRequestAuditContext.open(requestId)) {
            String answer;
            try {
                answer = chatGateway.chat(message);
            } catch (RuntimeException | Error modelFailure) {
                long durationMs = auditService.elapsedMillis(auditHandle);
                try {
                    durationMs = auditService.completeFailure(auditHandle, modelFailure);
                } catch (RuntimeException auditFailure) {
                    metrics.recordAuditWriteFailure("agent_failure");
                    modelFailure.addSuppressed(auditFailure);
                    log.error("Agent failure audit completion failed requestId={} errorType={}",
                            requestId, auditFailure.getClass().getSimpleName());
                }
                metrics.recordRequest("FAILED", durationMs);
                log.info("Agent request completed requestId={} outcome=FAILED provider={} model={} durationMs={} errorType={}",
                        requestId, provider, model, durationMs, modelFailure.getClass().getSimpleName());
                throw modelFailure;
            }

            long durationMs;
            try {
                durationMs = auditService.completeSuccess(auditHandle, answer);
            } catch (RuntimeException auditFailure) {
                metrics.recordAuditWriteFailure("agent_success");
                metrics.recordRequest("AUDIT_FAILED", auditService.elapsedMillis(auditHandle));
                log.error("Agent success audit completion failed requestId={} errorType={}",
                        requestId, auditFailure.getClass().getSimpleName());
                throw auditFailure;
            }

            metrics.recordRequest("SUCCESS", durationMs);
            log.info("Agent request completed requestId={} outcome=SUCCESS provider={} model={} durationMs={}",
                    requestId, provider, model, durationMs);
            return new AgentChatResult(requestId, answer);
        } finally {
            restoreRequestId(previousRequestId);
        }
    }

    private AgentChatResult failBeforeModel(
            AgentAuditHandle auditHandle,
            InvalidAgentMessageException validationFailure,
            String previousRequestId) {
        long durationMs = auditService.elapsedMillis(auditHandle);
        try {
            durationMs = auditService.completeFailure(auditHandle, validationFailure);
            log.info("Agent request completed requestId={} outcome=FAILED provider={} model={} durationMs={} errorType={}",
                    auditHandle.requestId(), provider, model, durationMs,
                    validationFailure.getClass().getSimpleName());
        } catch (RuntimeException auditFailure) {
            metrics.recordAuditWriteFailure("agent_failure");
            validationFailure.addSuppressed(auditFailure);
            log.error("Agent validation audit completion failed requestId={} errorType={}",
                    auditHandle.requestId(), auditFailure.getClass().getSimpleName());
        } finally {
            metrics.recordRequest("FAILED", durationMs);
            restoreRequestId(previousRequestId);
        }
        throw validationFailure;
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
