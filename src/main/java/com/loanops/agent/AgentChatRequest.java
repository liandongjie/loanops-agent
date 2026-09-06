package com.loanops.agent;

import com.loanops.conversation.ConversationHistoryMessage;
import com.loanops.policy.PolicyGroundingContext;

import java.util.List;

public record AgentChatRequest(
        List<ConversationHistoryMessage> history,
        String currentUserMessage,
        PolicyGroundingContext policyGroundingContext) {

    public AgentChatRequest {
        history = List.copyOf(history);
    }
}
