package com.loanops.dto;

/**
 * {@code LoanOpsAgentService} 完成一次非流式 Agent 请求后产生的内部结果。
 * 此时回答已经通过必要的政策引用校验，并已与成功会话轮次一起提交。
 * {@code AgentController} 会把它转换成 {@link AgentChatResponse}，因此它不是 Controller 直接返回的 HTTP 响应对象。
 *
 * @param conversationId 本轮所属会话的标识，客户端可在后续请求中继续使用
 * @param requestId 本次 Agent 请求的唯一标识，用于关联日志和审计记录
 * @param answer 本轮完成校验并成功提交的最终回答
 */
public record AgentChatResult(String conversationId, String requestId, String answer) {
}
