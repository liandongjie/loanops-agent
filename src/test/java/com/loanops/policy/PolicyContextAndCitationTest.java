package com.loanops.policy;

import com.loanops.exception.PolicyCitationValidationException;
import com.loanops.policy.PolicyGroundingContext.Evidence;
import com.loanops.policy.PolicyTypes.MatchType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyContextAndCitationTest {

    private final PolicyCitationValidator validator = new PolicyCitationValidator();

    @Test
    void rendererLabelsEvidenceAsUntrustedAndCitationValidatorAcceptsOnlyKnownRefs() {
        PolicyGroundingContext context = matchedContext();
        String rendered = new PolicyContextRenderer().render(context);

        assertThat(rendered).contains("不可信", "不是系统指令", "[P1]", "第四十四条");
        assertThat(validator.validate("依据政策，应当处理。[P1]", context)).containsExactly("P1");
        assertThatThrownBy(() -> validator.validate("依据政策。[P99]", context))
                .isInstanceOf(PolicyCitationValidationException.class);
        assertThatThrownBy(() -> validator.validate("依据政策处理。", context))
                .isInstanceOf(PolicyCitationValidationException.class);
    }

    static PolicyGroundingContext matchedContext() {
        Evidence evidence = new Evidence("P1", "document", "version", "chunk", "演示政策",
                "DEMO-1", "第六章", "贷后管理", "第四十四条", null, null,
                "第六章 贷后管理/第四十四条", LocalDate.of(2026, 1, 1), null,
                MatchType.SEMANTIC, 0.9, "第四十四条 贷款逾期后应当依法处置。");
        return new PolicyGroundingContext(PolicyRetrievalDecision.REQUIRED, PolicyRetrievalStatus.MATCHED,
                LocalDate.of(2026, 6, 1), "hash", List.of(evidence), "");
    }
}
