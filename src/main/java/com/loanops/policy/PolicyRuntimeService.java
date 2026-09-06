package com.loanops.policy;

import com.loanops.conversation.ConversationSnapshot;
import com.loanops.exception.PolicyRetrievalException;
import com.loanops.policy.PolicyTypes.RetrievalResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

@Service
@Profile("ai")
public class PolicyRuntimeService {

    private final PolicyRetrievalDecisionEngine decisionEngine;
    private final PolicyQueryBuilder queryBuilder;
    private final PolicyGroundingContextFactory contextFactory;
    private final PolicyCitationValidator citationValidator;
    private final PolicyRetrievalAuditService auditService;
    private final ObjectProvider<PolicyRetriever> retrieverProvider;
    private final Clock businessClock;
    private final boolean enabled;

    public PolicyRuntimeService(
            PolicyRetrievalDecisionEngine decisionEngine,
            PolicyQueryBuilder queryBuilder,
            PolicyGroundingContextFactory contextFactory,
            PolicyCitationValidator citationValidator,
            PolicyRetrievalAuditService auditService,
            ObjectProvider<PolicyRetriever> retrieverProvider,
            Clock businessClock,
            @Value("${loanops.policy.runtime.enabled:true}") boolean enabled) {
        this.decisionEngine = decisionEngine;
        this.queryBuilder = queryBuilder;
        this.contextFactory = contextFactory;
        this.citationValidator = citationValidator;
        this.auditService = auditService;
        this.retrieverProvider = retrieverProvider;
        this.businessClock = businessClock;
        this.enabled = enabled;
    }

    public PolicyRuntimePreparation prepare(String requestId, ConversationSnapshot snapshot, String message) {
        LocalDate asOfDate = LocalDate.now(businessClock);
        PolicyRetrievalDecision decision = enabled
                ? decisionEngine.decide(snapshot.history(), message)
                : PolicyRetrievalDecision.NOT_REQUIRED;
        String query = decision == PolicyRetrievalDecision.NOT_REQUIRED
                ? null : queryBuilder.build(snapshot.history(), message);
        PolicyRetrievalAuditHandle handle = auditService.begin(requestId, snapshot.conversationId(), decision,
                asOfDate, query == null ? null : PolicyHashing.sha256(query));

        if (decision == PolicyRetrievalDecision.NOT_REQUIRED) {
            PolicyGroundingContext context = contextFactory.notRun(asOfDate);
            auditService.complete(handle, PolicyRetrievalStatus.NOT_RUN, null, context, null);
            return new PolicyRuntimePreparation(context, handle);
        }

        PolicyRetriever retriever = retrieverProvider.getIfAvailable();
        if (retriever == null) {
            return retrievalFailed(decision, asOfDate, handle,
                    new IllegalStateException("PolicyRetriever is unavailable"));
        }
        try {
            RetrievalResult result = retriever.retrieve(query, asOfDate);
            PolicyGroundingContext context = contextFactory.create(decision, result);
            auditService.complete(handle, context.retrievalStatus(), result, context, null);
            return new PolicyRuntimePreparation(context, handle);
        } catch (RuntimeException failure) {
            return retrievalFailed(decision, asOfDate, handle, failure);
        }
    }

    public String validateAndRecord(String answer, PolicyRuntimePreparation preparation) {
        PolicyGroundingContext context = preparation.context();
        String completedAnswer = context.decision() == PolicyRetrievalDecision.SUPPLEMENTAL
                && (context.retrievalStatus() == PolicyRetrievalStatus.NO_MATCH
                || context.retrievalStatus() == PolicyRetrievalStatus.FAILED)
                && !answer.contains(context.notice())
                ? answer + "\n\n" + context.notice()
                : answer;
        Set<String> citations = citationValidator.validate(completedAnswer, context);
        auditService.markCitations(preparation.auditHandle().retrievalId(), citations);
        return completedAnswer;
    }

    private PolicyRuntimePreparation retrievalFailed(PolicyRetrievalDecision decision, LocalDate asOfDate,
                                                     PolicyRetrievalAuditHandle handle, RuntimeException failure) {
        PolicyGroundingContext context = contextFactory.failed(decision, asOfDate);
        auditService.complete(handle, PolicyRetrievalStatus.FAILED, null, context, failure);
        if (decision == PolicyRetrievalDecision.REQUIRED) throw new PolicyRetrievalException(failure);
        return new PolicyRuntimePreparation(context, handle);
    }
}
