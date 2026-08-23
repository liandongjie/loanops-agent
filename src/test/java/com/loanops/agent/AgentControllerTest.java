package com.loanops.agent;

import com.loanops.controller.AgentController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerTest {

    private final LoanOpsAgentService agentService = mock(LoanOpsAgentService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentController(agentService)).build();

    @Test
    void chat_returnsAgentAnswer() throws Exception {
        when(agentService.chat("LN-10001 本期应该还多少钱？")).thenReturn("本期应还 8500.00 元。");

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"LN-10001 本期应该还多少钱？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("本期应还 8500.00 元。"));

        verify(agentService).chat("LN-10001 本期应该还多少钱？");
    }

    @Test
    void chat_rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
