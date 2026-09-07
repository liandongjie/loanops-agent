package com.loanops.agent;

import com.loanops.conversation.ConversationHistoryMessage;
import com.loanops.policy.PolicyContextRenderer;
import com.loanops.policy.PolicyGroundingContext;
import com.loanops.policy.PolicyRetrievalDecision;
import com.loanops.policy.PolicyRetrievalStatus;
import com.loanops.tool.LoanOpsTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiAgentChatGatewayTest {

    @Test
    void injectsPolicyAsSystemDataThenChronologicalHistoryAndCurrentUserExactlyOnce() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        LoanOpsTools tools = mock(LoanOpsTools.class);
        when(builder.defaultTools(tools)).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.messages(anyList())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.content()).thenReturn("answer");
        SpringAiAgentChatGateway gateway = new SpringAiAgentChatGateway(
                builder, tools, new PolicyContextRenderer());
        List<ConversationHistoryMessage> history = List.of(
                new ConversationHistoryMessage(1, "USER", "first question"),
                new ConversationHistoryMessage(2, "ASSISTANT", "first answer"));
        PolicyGroundingContext context = new PolicyGroundingContext(
                PolicyRetrievalDecision.REQUIRED, PolicyRetrievalStatus.NO_MATCH,
                LocalDate.of(2026, 1, 1), "hash", List.of(), "no evidence");

        assertThat(gateway.chat(new AgentChatRequest(history, "current question", context))).isEqualTo("answer");

        verify(request).system(gateway.systemPrompt());
        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(request).messages(messages.capture());
        assertThat(messages.getValue()).hasSize(3);
        assertThat(messages.getValue().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.getValue().get(0).getText()).contains("POLICY_CONTEXT", "不可信");
        assertThat(messages.getValue().get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.getValue().get(1).getText()).isEqualTo("first question");
        assertThat(messages.getValue().get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.getValue().get(2).getText()).isEqualTo("first answer");
        verify(request, times(1)).user("current question");
        assertThat(messages.getValue()).noneMatch(message -> message.getText().equals("current question"));
    }
}
