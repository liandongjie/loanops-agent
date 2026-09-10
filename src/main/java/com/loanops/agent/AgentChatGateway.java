package com.loanops.agent;

import reactor.core.publisher.Flux;

public interface AgentChatGateway {

    String systemPrompt();

    String chat(AgentChatRequest request);

    Flux<String> stream(AgentChatRequest request);
}
