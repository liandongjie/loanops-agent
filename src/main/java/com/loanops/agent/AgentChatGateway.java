package com.loanops.agent;

public interface AgentChatGateway {

    String systemPrompt();

    String chat(AgentChatRequest request);
}
