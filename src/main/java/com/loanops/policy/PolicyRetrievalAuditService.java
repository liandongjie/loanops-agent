package com.loanops.policy;

import com.loanops.audit.AuditTimeProvider;
import com.loanops.policy.PolicyTypes.RetrievalHit;
import com.loanops.policy.PolicyTypes.RetrievalResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PolicyRetrievalAuditService {

    private final JdbcTemplate jdbc;
    private final AuditTimeProvider timeProvider;
    private final int topK;
    private final double threshold;
    private final String embeddingModel;
    private final String collection;
    private final String configHash;

    public PolicyRetrievalAuditService(
            JdbcTemplate jdbc,
            AuditTimeProvider timeProvider,
            @Value("${loanops.policy.retrieval-top-k:5}") int topK,
            @Value("${loanops.policy.runtime.score-threshold:0.0}") double threshold,
            @Value("${spring.ai.ollama.embedding.model:bge-m3}") String embeddingModel,
            @Value("${loanops.policy.qdrant.collection:loanops_policy_chunks}") String collection,
            @Value("${loanops.policy.runtime.context-max-evidence:5}") int maxEvidence,
            @Value("${loanops.policy.runtime.context-max-characters:6000}") int maxCharacters) {
        this.jdbc = jdbc;
        this.timeProvider = timeProvider;
        this.topK = topK;
        this.threshold = threshold;
        this.embeddingModel = embeddingModel;
        this.collection = collection;
        this.configHash = PolicyHashing.sha256(topK + "|" + threshold + "|" + maxEvidence + "|" + maxCharacters);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PolicyRetrievalAuditHandle begin(String requestId, String conversationId,
                                            PolicyRetrievalDecision decision, LocalDate asOfDate,
                                            String queryHash) {
        String retrievalId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO policy_retrieval_audit (
                    retrieval_id, request_id, conversation_id, decision, as_of_date, query_hash,
                    status, top_k, score_threshold, retrieval_config_hash, embedding_model,
                    index_collection, started_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'STARTED', ?, ?, ?, ?, ?, ?)
                """, retrievalId, requestId, conversationId, decision.name(), asOfDate, queryHash,
                topK, threshold, configHash, embeddingModel, collection, timeProvider.nowUtc());
        return new PolicyRetrievalAuditHandle(retrievalId, timeProvider.startNanos());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(PolicyRetrievalAuditHandle handle, PolicyRetrievalStatus status,
                         RetrievalResult result, PolicyGroundingContext context, Throwable failure) {
        int updated = jdbc.update("""
                UPDATE policy_retrieval_audit
                SET status = ?, context_hash = ?, completed_at = ?, duration_ms = ?, error_type = ?
                WHERE retrieval_id = ? AND status = 'STARTED'
                """, status.name(), context == null ? null : context.contextHash(), timeProvider.nowUtc(),
                timeProvider.elapsedMillis(handle.startedNanos()), failure == null ? null : failure.getClass().getSimpleName(),
                handle.retrievalId());
        if (updated != 1) throw new IllegalStateException("policy retrieval audit completion affected no rows");
        if (result != null) insertHits(handle.retrievalId(), result.hits(), context);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCitations(String retrievalId, Set<String> citedRefs) {
        for (String citationRef : citedRefs) {
            jdbc.update("""
                    UPDATE policy_retrieval_hit SET cited_in_answer = TRUE
                    WHERE retrieval_id = ? AND citation_ref = ?
                    """, retrievalId, citationRef);
        }
    }

    private void insertHits(String retrievalId, List<RetrievalHit> hits, PolicyGroundingContext context) {
        Map<String, PolicyGroundingContext.Evidence> selected = context == null ? Map.of() : context.evidence().stream()
                .collect(Collectors.toMap(PolicyGroundingContext.Evidence::chunkId, Function.identity()));
        for (int index = 0; index < hits.size(); index++) {
            RetrievalHit hit = hits.get(index);
            PolicyGroundingContext.Evidence evidence = selected.get(hit.match().chunk().chunkId());
            jdbc.update("""
                    INSERT INTO policy_retrieval_hit (
                        retrieval_id, rank_no, document_id, version_id, chunk_id, match_type,
                        score, selected_for_context, citation_ref, cited_in_answer
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE)
                    """, retrievalId, index + 1, hit.match().document().documentId(),
                    hit.match().version().versionId(), hit.match().chunk().chunkId(), hit.matchType().name(),
                    hit.score(), evidence != null, evidence == null ? null : evidence.citationRef());
        }
    }
}
