package com.loanops.policy;

import com.loanops.conversation.ConversationHistoryMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class PolicyRetrievalDecisionEngine {

    private static final Pattern NORMATIVE = Pattern.compile(
            "规定|依据|应当|允许|禁止|合规|办法|流程|按规定|政策|制度|监管|如何处置|怎么处理");
    private static final Pattern LOAN_NUMBER = Pattern.compile("(?i)\\bLN-[A-Z0-9-]+\\b");

    public PolicyRetrievalDecision decide(List<ConversationHistoryMessage> history, String currentUserMessage) {
        if (!NORMATIVE.matcher(currentUserMessage).find()) {
            return PolicyRetrievalDecision.NOT_REQUIRED;
        }
        if (LOAN_NUMBER.matcher(currentUserMessage).find() || priorUserHasLoanNumber(history)) {
            return PolicyRetrievalDecision.SUPPLEMENTAL;
        }
        return PolicyRetrievalDecision.REQUIRED;
    }

    private boolean priorUserHasLoanNumber(List<ConversationHistoryMessage> history) {
        return history.stream()
                .filter(message -> "USER".equals(message.role()))
                .anyMatch(message -> LOAN_NUMBER.matcher(message.content()).find());
    }
}
