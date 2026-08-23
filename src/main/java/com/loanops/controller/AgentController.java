package com.loanops.controller;

import com.loanops.agent.LoanOpsAgentService;
import com.loanops.dto.AgentChatRequest;
import com.loanops.dto.AgentChatResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/agent")
@Profile("ai")
public class AgentController {

    private final LoanOpsAgentService agentService;

    public AgentController(LoanOpsAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public AgentChatResponse chat(@RequestBody AgentChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }
        return new AgentChatResponse(agentService.chat(request.message()));
    }
}
