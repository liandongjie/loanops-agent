package com.loanops.agent;

import com.loanops.conversation.ConversationHistoryMessage;

import java.util.List;

public interface AgentChatGateway {

    String systemPrompt();

    String chat(List<ConversationHistoryMessage> history, String message);
}
