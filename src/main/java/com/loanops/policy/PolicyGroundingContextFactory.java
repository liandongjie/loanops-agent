package com.loanops.policy;

import com.loanops.policy.PolicyGroundingContext.Evidence;
import com.loanops.policy.PolicyTypes.RetrievalHit;
import com.loanops.policy.PolicyTypes.RetrievalResult;
import com.loanops.policy.PolicyTypes.StoredChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class PolicyGroundingContextFactory {

    public static final String NO_MATCH_NOTICE = "当前政策知识库没有找到足够适用依据，因此无法确认政策部分。";
    public static final String FAILED_NOTICE = "政策检索当前不可用，无法确认政策部分。";

    private final int maxEvidence;
    private final int maxCharacters;
    private final double threshold;

    public PolicyGroundingContextFactory(
            @Value("${loanops.policy.runtime.context-max-evidence:5}") int maxEvidence,
            @Value("${loanops.policy.runtime.context-max-characters:6000}") int maxCharacters,
            @Value("${loanops.policy.runtime.score-threshold:0.60}") double threshold) {
        if (maxEvidence < 1 || maxCharacters < 1 || threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("Invalid policy context limits");
        }
        this.maxEvidence = maxEvidence;
        this.maxCharacters = maxCharacters;
        this.threshold = threshold;
    }

    public PolicyGroundingContext notRun(LocalDate asOfDate) {
        return empty(PolicyRetrievalDecision.NOT_REQUIRED, PolicyRetrievalStatus.NOT_RUN, asOfDate, "");
    }

    public PolicyGroundingContext failed(PolicyRetrievalDecision decision, LocalDate asOfDate) {
        return empty(decision, PolicyRetrievalStatus.FAILED, asOfDate, FAILED_NOTICE);
    }

    public PolicyGroundingContext create(PolicyRetrievalDecision decision, RetrievalResult result) {
        List<Evidence> evidence = new ArrayList<>();
        int characters = 0;
        for (RetrievalHit hit : result.hits()) {
            if (evidence.size() >= maxEvidence || hit.score() < threshold) continue;
            StoredChunk stored = hit.match();
            if (characters + stored.chunk().content().length() > maxCharacters) continue;
            String citationRef = "P" + (evidence.size() + 1);
            evidence.add(new Evidence(citationRef, stored.document().documentId(), stored.version().versionId(),
                    stored.chunk().chunkId(), stored.document().title(), stored.version().documentNumber(),
                    stored.chunk().chapterNo(), stored.chunk().chapterTitle(), stored.chunk().articleNo(),
                    stored.chunk().paragraphNo(), stored.chunk().itemNo(), stored.chunk().sectionPath(),
                    stored.version().effectiveFrom(), stored.version().effectiveTo(), hit.matchType(), hit.score(),
                    stored.chunk().content()));
            characters += stored.chunk().content().length();
        }
        if (evidence.isEmpty()) {
            return empty(decision, PolicyRetrievalStatus.NO_MATCH, result.asOfDate(), NO_MATCH_NOTICE);
        }
        String canonical = evidence.stream().map(item -> String.join("|", item.citationRef(), item.documentId(),
                item.versionId(), item.chunkId(), item.matchType().name(), Double.toString(item.score()),
                item.content())).reduce((left, right) -> left + "\n" + right).orElse("");
        return new PolicyGroundingContext(decision, PolicyRetrievalStatus.MATCHED, result.asOfDate(),
                PolicyHashing.sha256(canonical), evidence, "");
    }

    public double threshold() {
        return threshold;
    }

    private PolicyGroundingContext empty(PolicyRetrievalDecision decision, PolicyRetrievalStatus status,
                                         LocalDate asOfDate, String notice) {
        String hash = PolicyHashing.sha256(decision + "|" + status + "|" + asOfDate + "|" + notice);
        return new PolicyGroundingContext(decision, status, asOfDate, hash, List.of(), notice);
    }
}
