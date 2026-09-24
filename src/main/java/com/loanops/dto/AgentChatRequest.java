package com.loanops.dto;

/**
 * {@code POST /api/agent/chat} 和流式聊天接口接收的 HTTP 请求体。
 * {@code AgentController} 只从中取出会话标识和本轮消息，再交给 {@code LoanOpsAgentService}；
 * 它不同于 {@link com.loanops.agent.AgentChatRequest}，后者还包含会话历史和政策上下文，
 * 是业务服务构造给模型网关使用的内部对象。
 *
 * @param conversationId 要继续的会话标识；为空时，业务服务会为本轮创建新会话
 * @param message 用户本轮输入，长度和空白校验由 {@code LoanOpsAgentService} 执行
 */
public record AgentChatRequest(String conversationId, String message) {
}
