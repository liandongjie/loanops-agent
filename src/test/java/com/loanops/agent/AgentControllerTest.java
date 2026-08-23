package com.loanops.agent;

import com.loanops.controller.AgentController;
import com.loanops.dto.AgentChatResult;
import com.loanops.exception.GlobalExceptionHandler;
import com.loanops.exception.InvalidAgentMessageException;
import com.loanops.observability.AgentRequestCorrelationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerTest {

    private static final String MESSAGE = "LN-10001 current repayment?";

    private final LoanOpsAgentService agentService = mock(LoanOpsAgentService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentController(agentService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new AgentRequestCorrelationFilter())
            .build();

    @Test
    void chatKeepsResponseBodyCompatibleAndReturnsTheSameCorrelationIdPassedToService() throws Exception {
        when(agentService.chatWithRequestId(anyString(), eq(MESSAGE)))
                .thenAnswer(invocation -> new AgentChatResult(invocation.getArgument(0), "8500.00"));

        MvcResult result = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + MESSAGE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.answer").value("8500.00"))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).matches("[0-9a-f-]{36}");
        verify(agentService).chatWithRequestId(requestId, MESSAGE);
    }

    @Test
    void blankMessageStillReturnsCorrelationIdAndStableValidationError() throws Exception {
        doThrow(new InvalidAgentMessageException())
                .when(agentService).chatWithRequestId(anyString(), eq("   "));

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("INVALID_AGENT_MESSAGE"));
    }
    @Test
    void clientSuppliedCorrelationIdIsReplacedByServerGeneratedId() throws Exception {
        when(agentService.chatWithRequestId(anyString(), eq(MESSAGE)))
                .thenAnswer(invocation -> new AgentChatResult(invocation.getArgument(0), "8500.00"));

        MvcResult result = mockMvc.perform(post("/api/agent/chat")
                        .header("X-Request-Id", "client-controlled-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + MESSAGE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).matches("[0-9a-f-]{36}");
        assertThat(requestId).isNotEqualTo("client-controlled-id");
        verify(agentService).chatWithRequestId(requestId, MESSAGE);
    }

}
