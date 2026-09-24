package com.loanops.dto;

import java.time.LocalDateTime;

/**
 * 一次 Tool 调用的审计结果，作为 {@link AgentAuditResponse#tools()} 中的明细返回。
 * 它记录大模型在某次 Agent 请求中调用了哪个只读 Tool、查询哪笔贷款以及调用结果，
 * 不承载 Tool 返回的贷款业务事实本身。
 *
 * @param sequenceNo 此 Tool 调用在同一 Agent 请求中的顺序编号
 * @param toolName 被调用的 Tool 名称
 * @param loanNo 此次 Tool 调用查询的业务贷款编号
 * @param startedAt Tool 调用开始时间
 * @param completedAt Tool 调用完成时间；仍在执行时为空
 * @param status 调用状态，当前可能为 {@code STARTED}、{@code SUCCESS} 或 {@code FAILED}
 * @param durationMs Tool 调用耗时（毫秒）；尚未完成时为空
 * @param errorType 失败时记录的异常类型；成功或尚未完成时为空
 */
public record AgentToolAuditResponse(
        Integer sequenceNo,
        String toolName,
        String loanNo,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String status,
        Long durationMs,
        String errorType) {
}
