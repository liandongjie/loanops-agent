package com.loanops.policy;

import com.loanops.conversation.ConversationHistoryMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PolicyQueryBuilder {

    private final int priorUserLimit;
    private final int maxCharacters;

    public PolicyQueryBuilder(
            @Value("${loanops.policy.runtime.query-prior-user-limit:3}") int priorUserLimit,
            @Value("${loanops.policy.runtime.query-max-characters:1200}") int maxCharacters) {
        if (priorUserLimit < 0 || maxCharacters < 1) throw new IllegalArgumentException("Invalid policy query limits");
        this.priorUserLimit = priorUserLimit;
        this.maxCharacters = maxCharacters;
    }

    public String build(List<ConversationHistoryMessage> history, String currentUserMessage) {
        List<String> priorUsers = history.stream()
                .filter(message -> "USER".equals(message.role()))
                .map(ConversationHistoryMessage::content)
                .toList();
        int from = Math.max(0, priorUsers.size() - priorUserLimit);
        List<String> parts = new ArrayList<>(priorUsers.subList(from, priorUsers.size()));
        parts.add(currentUserMessage);
        String joined = String.join("\n", parts);
        return joined.length() <= maxCharacters ? joined : joined.substring(joined.length() - maxCharacters);
    }
}
