package com.loanops.controller;

import com.loanops.agent.AgentStreamEvent;
import com.loanops.agent.LoanOpsAgentService;
import com.loanops.dto.AgentChatRequest;
import com.loanops.dto.AgentChatResponse;
import com.loanops.dto.AgentChatResult;
import com.loanops.observability.AgentRequestCorrelationFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

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
        String conversationId = request == null ? null : request.conversationId();
        String message = request == null ? null : request.message();
        AgentChatResult result = agentService.chatWithRequestId(requestId, conversationId, message);
        return ResponseEntity.ok(new AgentChatResponse(
                result.conversationId(), result.requestId(), result.answer()));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<Object>>> stream(
            @RequestBody(required = false) AgentChatRequest request,
            @RequestAttribute(AgentRequestCorrelationFilter.REQUEST_ATTRIBUTE) String requestId) {
        String conversationId = request == null ? null : request.conversationId();
        String message = request == null ? null : request.message();
        Flux<ServerSentEvent<Object>> events = agentService
                .streamWithRequestId(requestId, conversationId, message)
                .map(this::toServerSentEvent);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(events);
    }

    private ServerSentEvent<Object> toServerSentEvent(AgentStreamEvent event) {
        return ServerSentEvent.builder(event.data())
                .event(event.event())
                .build();
    }
}
