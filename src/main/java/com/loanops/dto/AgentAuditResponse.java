package com.loanops.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次 Agent 请求的只读审计查询结果，由 {@code AgentAuditService} 从 Agent 主审计记录
 * 和关联的 Tool 审计记录组装，并由 {@code GET /api/agent/audits/{requestId}} 返回。
 * 它用于追踪请求何时执行、使用哪个模型、是否成功以及使用过哪些 Tool，
 * 不等同于聊天接口返回的 {@link AgentChatResponse}。
 *
 * @param requestId 本次 Agent 请求的唯一标识，也是查询这条审计记录的键
 * @param startedAt Agent 请求开始时间
 * @param completedAt Agent 请求完成时间；状态仍为 {@code STARTED} 时为空
 * @param status 请求状态，当前可能为 {@code STARTED}、{@code SUCCESS} 或 {@code FAILED}
 * @param durationMs 从请求开始到完成的耗时（毫秒）；尚未完成时为空
 * @param businessDate 本次请求使用的业务日期
 * @param provider 本次请求配置的模型服务提供方
 * @param model 本次请求配置的模型名称
 * @param messageLength 用户消息的长度
 * @param answerLength 成功回答的长度；失败或尚未完成时为空
 * @param messageHash 用户消息内容的 SHA-256 指纹，用于核对内容而不必保存原文
 * @param answerHash 成功回答内容的 SHA-256 指纹；失败或尚未完成时为空
 * @param messageText 用户消息原文；只有启用审计原文保存时才会返回
 * @param answerText 成功回答原文；只有启用审计原文保存时才会返回
 * @param errorType 失败时记录的异常类型；成功或尚未完成时为空
 * @param conversationId 本次请求所属会话的标识；在创建或读取会话前失败时可能为空
 * @param historyFromSequence 本轮送入 Agent 的历史消息起始序号；没有历史时为空
 * @param historyToSequence 本轮送入 Agent 的历史消息结束序号；没有历史时为空
 * @param historyHash 本轮实际使用的会话历史的 SHA-256 指纹；在创建或读取会话前失败时可能为空
 * @param systemPromptHash 本轮使用的系统提示词的 SHA-256 指纹；在创建或读取会话前失败时可能为空
 * @param tools 本次 Agent 请求关联的 Tool 调用审计明细
 */
public record AgentAuditResponse(
        String requestId,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String status,
        Long durationMs,
        LocalDate businessDate,
        String provider,
        String model,
        Integer messageLength,
        Integer answerLength,
        String messageHash,
        String answerHash,
        String messageText,
        String answerText,
        String errorType,
        String conversationId,
        Integer historyFromSequence,
        Integer historyToSequence,
        String historyHash,
        String systemPromptHash,
        List<AgentToolAuditResponse> tools) {
}
