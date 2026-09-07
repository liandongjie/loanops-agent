package com.loanops.agent;

import com.loanops.controller.AgentController;
import com.loanops.dto.AgentChatResult;
import com.loanops.exception.AgentProviderUnavailableException;
import com.loanops.exception.ConversationConflictException;
import com.loanops.exception.ConversationNotFoundException;
import com.loanops.exception.GlobalExceptionHandler;
import com.loanops.exception.InvalidAgentMessageException;
import com.loanops.exception.PolicyRetrievalException;
import com.loanops.observability.AgentRequestCorrelationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private static final String CONVERSATION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    private final LoanOpsAgentService agentService = mock(LoanOpsAgentService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentController(agentService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new AgentRequestCorrelationFilter())
            .build();

    @Test
    void messageOnlyClientCreatesConversationAndReceivesServerCorrelationIds() throws Exception {
        when(agentService.chatWithRequestId(anyString(), isNull(), eq(MESSAGE)))
                .thenAnswer(invocation -> new AgentChatResult(
                        CONVERSATION_ID, invocation.getArgument(0), "8500.00"));

        MvcResult result = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + MESSAGE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.answer").value("8500.00"))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).matches("[0-9a-f-]{36}");
        assertThat(result.getResponse().getContentAsString()).contains(requestId);
        verify(agentService).chatWithRequestId(requestId, null, MESSAGE);
    }

    @Test
    void suppliedConversationIdContinuesThatConversation() throws Exception {
        when(agentService.chatWithRequestId(anyString(), eq(CONVERSATION_ID), eq(MESSAGE)))
                .thenAnswer(invocation -> new AgentChatResult(
                        CONVERSATION_ID, invocation.getArgument(0), "answer"));

        MvcResult result = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + CONVERSATION_ID
                                + "\",\"message\":\"" + MESSAGE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID))
                .andReturn();

        verify(agentService).chatWithRequestId(
                result.getResponse().getHeader("X-Request-Id"), CONVERSATION_ID, MESSAGE);
    }

    @Test
    void blankMessagePreservesStableValidationError() throws Exception {
        doThrow(new InvalidAgentMessageException())
                .when(agentService).chatWithRequestId(anyString(), isNull(), eq("   "));

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("INVALID_AGENT_MESSAGE"));
    }

    @Test
    void unknownConversationReturnsStableNotFoundResponse() throws Exception {
        doThrow(new ConversationNotFoundException(CONVERSATION_ID))
                .when(agentService).chatWithRequestId(anyString(), eq(CONVERSATION_ID), eq(MESSAGE));

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + CONVERSATION_ID
                                + "\",\"message\":\"" + MESSAGE + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void optimisticConflictReturnsStableConflictResponse() throws Exception {
        doThrow(new ConversationConflictException(CONVERSATION_ID, 0, 0))
                .when(agentService).chatWithRequestId(anyString(), eq(CONVERSATION_ID), eq(MESSAGE));

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + CONVERSATION_ID
                                + "\",\"message\":\"" + MESSAGE + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONVERSATION_CONFLICT"));
    }

    @Test
    void providerFailureReturnsStable503WithoutLeakingCause() throws Exception {
        doThrow(new AgentProviderUnavailableException(
                new ResourceAccessException("https://provider.example?api_key=secret timed out")))
                .when(agentService).chatWithRequestId(anyString(), isNull(), eq(MESSAGE));

        MvcResult result = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + MESSAGE + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AGENT_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Agent provider is temporarily unavailable"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret", "provider.example");
    }

    @Test
    void requiredPolicyFailureReturnsStable503WithoutLeakingCause() throws Exception {
        doThrow(new PolicyRetrievalException(new RuntimeException("http://qdrant.internal failed")))
                .when(agentService).chatWithRequestId(anyString(), isNull(), eq(MESSAGE));

        MvcResult result = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + MESSAGE + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("POLICY_RETRIEVAL_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        "Required policy retrieval is temporarily unavailable"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("qdrant.internal");
    }
    @Test
    void clientSuppliedRequestIdIsStillReplacedByServerGeneratedId() throws Exception {
        when(agentService.chatWithRequestId(anyString(), isNull(), eq(MESSAGE)))
                .thenAnswer(invocation -> new AgentChatResult(
                        CONVERSATION_ID, invocation.getArgument(0), "8500.00"));

        MvcResult result = mockMvc.perform(post("/api/agent/chat")
                        .header("X-Request-Id", "client-controlled-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + MESSAGE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).matches("[0-9a-f-]{36}").isNotEqualTo("client-controlled-id");
        verify(agentService).chatWithRequestId(requestId, null, MESSAGE);
    }
}
