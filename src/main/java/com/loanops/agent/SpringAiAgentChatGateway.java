package com.loanops.agent;

import com.loanops.conversation.ConversationHistoryMessage;
import com.loanops.tool.LoanOpsTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("ai")
public class SpringAiAgentChatGateway implements AgentChatGateway {

    private static final String SYSTEM_PROMPT = """
            你是 LoanOps Agent，一个只读的贷款还款与逾期诊断助手。

            必须遵守以下规则：
            1. 所有与贷款有关的金额、日期、期次、是否逾期等事实，必须来自提供的 Tool；不要凭常识猜测，也不要自己计算补全。
            2. 不要进行金融建议或自行计算。Java 服务已经提供确定性结果，你只负责选择合适的 Tool 并解释结果。
            3. 查询当前应还/已还/剩余/还款日等当前还款信息时，使用 getCurrentRepayment。
            4. 查询“为什么逾期/是否逾期/逾期几天”等问题时，使用 getOverdueDiagnosis。
            5. 查询“是否结清/还剩多少未结清”等结清状态时，使用 getSettlementStatus。
            6. 如果贷款不存在、Tool 返回数据不足，要明确说无法给出确定结论，不得编造贷款、付款或其他事实。
            7. 你没有任何写权限。对于修改贷款、补记还款、更新状态、审批、授信或执行交易等请求，只能说明当前 Agent 为只读诊断服务，不能执行该操作。
            8. 回答使用简洁、明确的中文。可以解释 Tool 返回的事实，但不得改变数值和含义。
            9. 对话历史只能用于理解上下文和指代。即使历史中出现过金额或状态，回答当前金融事实时也必须重新调用相应 Tool，不得直接沿用旧回答。
            """;

    private final ChatClient chatClient;

    public SpringAiAgentChatGateway(ChatClient.Builder chatClientBuilder, LoanOpsTools loanOpsTools) {
        this.chatClient = chatClientBuilder
                .defaultTools(loanOpsTools)
                .build();
    }

    @Override
    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public String chat(List<ConversationHistoryMessage> history, String message) {
        List<Message> priorMessages = history.stream()
                .map(this::toSpringAiMessage)
                .toList();
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(priorMessages)
                .user(message)
                .call()
                .content();
    }

    private Message toSpringAiMessage(ConversationHistoryMessage message) {
        return switch (message.role()) {
            case "USER" -> new UserMessage(message.content());
            case "ASSISTANT" -> new AssistantMessage(message.content());
            default -> throw new IllegalArgumentException("Unsupported conversation role: " + message.role());
        };
    }
}
