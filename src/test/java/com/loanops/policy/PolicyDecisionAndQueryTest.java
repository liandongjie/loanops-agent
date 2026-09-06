package com.loanops.policy;

import com.loanops.conversation.ConversationHistoryMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDecisionAndQueryTest {

    private final PolicyRetrievalDecisionEngine engine = new PolicyRetrievalDecisionEngine();

    @Test
    void classifiesFinancialPolicyMixedAndContextualRequests() {
        assertThat(engine.decide(List.of(), "LN-10001 当前应该还多少钱？"))
                .isEqualTo(PolicyRetrievalDecision.NOT_REQUIRED);
        assertThat(engine.decide(List.of(), "LN-10002 为什么逾期？"))
                .isEqualTo(PolicyRetrievalDecision.NOT_REQUIRED);
        assertThat(engine.decide(List.of(), "个人贷款逾期以后按规定应该如何处置？"))
                .isEqualTo(PolicyRetrievalDecision.REQUIRED);
        assertThat(engine.decide(List.of(), "LN-10002 已经逾期了，按照规定现在应该怎么处理？"))
                .isEqualTo(PolicyRetrievalDecision.SUPPLEMENTAL);
        assertThat(engine.decide(List.of(
                        new ConversationHistoryMessage(1, "USER", "LN-10002 为什么逾期？"),
                        new ConversationHistoryMessage(2, "ASSISTANT", "历史回答")),
                "那按照规定应该怎么办？"))
                .isEqualTo(PolicyRetrievalDecision.SUPPLEMENTAL);
    }

    @Test
    void retrievalQueryUsesBoundedPriorUserMessagesButNeverAssistantText() {
        PolicyQueryBuilder builder = new PolicyQueryBuilder(2, 200);
        String query = builder.build(List.of(
                new ConversationHistoryMessage(1, "USER", "较早问题"),
                new ConversationHistoryMessage(2, "ASSISTANT", "不应进入查询的回答"),
                new ConversationHistoryMessage(3, "USER", "LN-10002 为什么逾期？"),
                new ConversationHistoryMessage(4, "ASSISTANT", "旧 Tool 结果 3500 元")),
                "那按照规定应该怎么办？");

        assertThat(query).isEqualTo("较早问题\nLN-10002 为什么逾期？\n那按照规定应该怎么办？");
        assertThat(query).doesNotContain("不应进入", "3500");
    }
}
