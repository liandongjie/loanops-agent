package com.loanops.agent;

import com.loanops.conversation.ConversationHistoryMessage;
import com.loanops.tool.LoanOpsTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

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
    void injectsSystemThenChronologicalHistoryAndCurrentUserExactlyOnce() {
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
        SpringAiAgentChatGateway gateway = new SpringAiAgentChatGateway(builder, tools);
        List<ConversationHistoryMessage> history = List.of(
                new ConversationHistoryMessage(1, "USER", "first question"),
                new ConversationHistoryMessage(2, "ASSISTANT", "first answer"));

        assertThat(gateway.chat(history, "current question")).isEqualTo("answer");

        verify(request).system(gateway.systemPrompt());
        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(request).messages(messages.capture());
        assertThat(messages.getValue()).hasSize(2);
        assertThat(messages.getValue().get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.getValue().get(0).getText()).isEqualTo("first question");
        assertThat(messages.getValue().get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.getValue().get(1).getText()).isEqualTo("first answer");
        verify(request, times(1)).user("current question");
        assertThat(messages.getValue()).noneMatch(message -> message.getText().equals("current question"));
    }
}
