package com.loanops.policy;

import com.loanops.policy.PolicyTypes.MatchType;

import java.time.LocalDate;
import java.util.List;

public record PolicyGroundingContext(
        PolicyRetrievalDecision decision,
        PolicyRetrievalStatus retrievalStatus,
        LocalDate asOfDate,
        String contextHash,
        List<Evidence> evidence,
        String notice) {

    public PolicyGroundingContext {
        evidence = List.copyOf(evidence);
    }

    public boolean requiresCitation() {
        return decision != PolicyRetrievalDecision.NOT_REQUIRED
                && retrievalStatus == PolicyRetrievalStatus.MATCHED;
    }

    public record Evidence(
            String citationRef,
            String documentId,
            String versionId,
            String chunkId,
            String title,
            String documentNumber,
            String chapterNo,
            String chapterTitle,
            String articleNo,
            Integer paragraphNo,
            String itemNo,
            String sectionPath,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            MatchType matchType,
            double score,
            String content) {}
}
