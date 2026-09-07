package com.loanops.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanops.conversation.ConversationHistoryMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDecisionAndQueryTest {

    private static final Path ROUTER_CASES = Path.of("evaluation/policy-router-hardening-cases.json");
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
    void routesKnownFalseNegativesAndTheirParaphrases() {
        assertDecisions(PolicyRetrievalDecision.REQUIRED, List.of(
                "借款人短期资金周转困难，想延后到期日，需要履行什么程序？",
                "贷款已经全部付清但抵押登记还挂着，机构应该做什么？",
                "能不能只根据晚还了几天就认定贷款资产类别？",
                "把催款工作交给外部公司后，银行还要怎样管控？",
                "贷款还清后需要出具什么并办理什么手续？",
                "想把贷款期限往后延，需要提交哪些材料？",
                "债务结清以后，银行应该办理哪些后续手续？",
                "是否可以仅凭逾期天数进行风险分类？",
                "委托第三方催收后，贷款机构需要如何监督？",
                "全部偿还后要不要出具结清证明？",
                "贷款逾期后怎么处理？",
                "贷款还清后要办理哪些手续？"));
    }

    @Test
    void keepsFinancialNearNegativesOutOfPolicyRetrieval() {
        assertDecisions(PolicyRetrievalDecision.NOT_REQUIRED, List.of(
                "LN-10001 当前应该还多少钱？",
                "能不能告诉我 LN-10002 逾期几天？",
                "LN-10003 是否已经结清？",
                "LN-10001 需要还多少本金？",
                "LN-10002 应该在哪天还款？",
                "请问 LN-10003 可以查询到结清状态吗？",
                "LN-10001 现在还要多少钱？"));
    }

    @Test
    void loanContextUsesCurrentAndPriorUserMessagesButNotAssistantMessages() {
        assertThat(engine.decide(List.of(), "LN-10001 结清后应该办理哪些手续？"))
                .isEqualTo(PolicyRetrievalDecision.SUPPLEMENTAL);
        assertThat(engine.decide(List.of(
                        new ConversationHistoryMessage(1, "USER", "帮我看一下 LN-10002")),
                "逾期后需要采取哪些处置措施？"))
                .isEqualTo(PolicyRetrievalDecision.SUPPLEMENTAL);
        assertThat(engine.decide(List.of(
                        new ConversationHistoryMessage(1, "USER", "贷款逾期了"),
                        new ConversationHistoryMessage(2, "ASSISTANT", "已查询 LN-10002")),
                "接下来应该办理哪些手续？"))
                .isEqualTo(PolicyRetrievalDecision.REQUIRED);
    }

    @Test
    void routerRegressionSetPasses() throws Exception {
        RouterDataset dataset = new ObjectMapper().readValue(ROUTER_CASES.toFile(), RouterDataset.class);

        for (RouterCase routerCase : dataset.cases()) {
            List<ConversationHistoryMessage> history = new ArrayList<>();
            for (int index = 0; index < routerCase.history().size(); index++) {
                RouterHistory item = routerCase.history().get(index);
                history.add(new ConversationHistoryMessage(index + 1, item.role(), item.content()));
            }
            assertThat(engine.decide(history, routerCase.message()))
                    .as(routerCase.id())
                    .isEqualTo(PolicyRetrievalDecision.valueOf(routerCase.expectedDecision()));
        }
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

    private void assertDecisions(PolicyRetrievalDecision expected, List<String> messages) {
        for (String message : messages) {
            assertThat(engine.decide(List.of(), message)).as(message).isEqualTo(expected);
        }
    }

    record RouterDataset(List<RouterCase> cases) {}
    record RouterCase(String id, List<RouterHistory> history, String message, String expectedDecision) {}
    record RouterHistory(String role, String content) {}
}
