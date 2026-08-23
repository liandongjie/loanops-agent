package com.loanops.controller;

import com.loanops.agent.LoanOpsAgentService;
import com.loanops.dto.AgentChatRequest;
import com.loanops.dto.AgentChatResponse;
import com.loanops.dto.AgentChatResult;
import com.loanops.observability.AgentRequestCorrelationFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
@Profile("ai")
public class AgentController {

    private final LoanOpsAgentService agentService;

    public AgentController(LoanOpsAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AgentChatResponse> chat(
            @RequestBody(required = false) AgentChatRequest request,
            @RequestAttribute(AgentRequestCorrelationFilter.REQUEST_ATTRIBUTE) String requestId) {
        String message = request == null ? null : request.message();
        AgentChatResult result = agentService.chatWithRequestId(requestId, message);
        return ResponseEntity.ok(new AgentChatResponse(result.answer()));
    }
}
