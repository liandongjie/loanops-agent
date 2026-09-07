package com.loanops.policy;

import com.loanops.conversation.ConversationHistoryMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class PolicyRetrievalDecisionEngine {

    private static final Pattern EXPLICIT_POLICY_SIGNAL = Pattern.compile(
            "规定|依据|合规|政策|制度|监管|规程|办法|规范|流程");
    private static final Pattern EXPLICIT_POLICY_REFERENCE = Pattern.compile(
            "第[一二三四五六七八九十百千万零〇0-9]+条|《[^》]+》|\\b(?!LN-)[A-Z][A-Z0-9-]*-\\d{4}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NORMATIVE_MODAL = Pattern.compile(
            "应当|应该|需要|需不需要|要不要|还要|要|能不能|能否|可不可以|是否|可以|可否|必须|不得|禁止|允许|怎么|怎样|如何");
    private static final Pattern POLICY_OR_PROCESS_ACTION = Pattern.compile(
            "履行|办理|提交|出具|处置|处理|管控|监督|认定|分类|审查|受理|反馈|登记|保存|核验|提示|"
                    + "报送|审批|执行|实施|申请|程序|手续|材料|条件|措施|步骤|责任|做什么|怎么办|怎么做");
    private static final Pattern LOAN_NUMBER = Pattern.compile("(?i)\\bLN-[A-Z0-9-]+\\b");

    public PolicyRetrievalDecision decide(List<ConversationHistoryMessage> history, String currentUserMessage) {
        if (!hasPolicyIntent(currentUserMessage)) {
            return PolicyRetrievalDecision.NOT_REQUIRED;
        }
        if (hasLoanNumber(currentUserMessage) || priorUserHasLoanNumber(history)) {
            return PolicyRetrievalDecision.SUPPLEMENTAL;
        }
        return PolicyRetrievalDecision.REQUIRED;
    }

    private boolean hasPolicyIntent(String message) {
        return EXPLICIT_POLICY_SIGNAL.matcher(message).find()
                || EXPLICIT_POLICY_REFERENCE.matcher(message).find()
                || hasImplicitNormativeIntent(message);
    }

    private boolean hasImplicitNormativeIntent(String message) {
        return NORMATIVE_MODAL.matcher(message).find()
                && POLICY_OR_PROCESS_ACTION.matcher(message).find();
    }

    private boolean hasLoanNumber(String message) {
        return LOAN_NUMBER.matcher(message).find();
    }

    private boolean priorUserHasLoanNumber(List<ConversationHistoryMessage> history) {
        return history.stream()
                .filter(message -> "USER".equals(message.role()))
                .anyMatch(message -> hasLoanNumber(message.content()));
    }
}
