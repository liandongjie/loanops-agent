package com.loanops.dto;

/**
 * 非流式聊天接口成功时返回给 HTTP 客户端的响应体。
 * {@code AgentController} 根据 {@link AgentChatResult} 创建本对象；流式接口使用 SSE 事件，
 * 不使用这个响应结构。
 *
 * @param conversationId 本轮所属会话的标识，后续请求可用它继续同一会话
 * @param requestId 本次请求的唯一标识，可用于查询对应的 Agent 审计
 * @param answer 返回给客户端的最终回答
 */
public record AgentChatResponse(String conversationId, String requestId, String answer) {
}
