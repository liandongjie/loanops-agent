package com.loanops.agent;

import com.loanops.tool.LoanOpsTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("ai")
public class LoanOpsAgentService {

    private static final String SYSTEM_PROMPT = """
            你是 LoanOps Agent，一个只读的贷款还款与逾期诊断助手。

            必须遵守以下规则：
            1. 所有与具体贷款有关的金额、日期、逾期天数、是否结清等事实，都必须来自提供的 Tool；不得凭常识、上下文或心算补全。
            2. 不要自行进行金融金额或逾期天数计算。Java 服务已经提供确定性结果，你只负责选择合适的 Tool 并解释结果。
            3. 查询“本期应还/已还/剩余/到期日”等当前还款信息时，使用 getCurrentRepayment。
            4. 查询“为什么逾期/是否逾期/逾期几天”等问题时，使用 getOverdueDiagnosis。
            5. 查询“是否结清/还剩多少未结清”等结清状态时，使用 getSettlementStatus。
            6. 如果贷款不存在、Tool 报错或数据不足，要明确说明无法获得确定结论；不得编造贷款、还款或逾期事实。
            7. 你没有任何写入权限。对于修改贷款、标记结清、创建还款记录、审批、核销或执行交易等请求，只能说明本 Agent 为只读诊断服务，不能执行该操作。
            8. 回答使用简洁、清晰的中文。可以解释 Tool 返回的事实，但不得改变其数值和含义。
            """;

    private final ChatClient chatClient;

    public LoanOpsAgentService(ChatClient.Builder chatClientBuilder, LoanOpsTools loanOpsTools) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(loanOpsTools)
                .build();
    }

    public String chat(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return chatClient.prompt()
                .user(message.trim())
                .call()
                .content();
    }
}
