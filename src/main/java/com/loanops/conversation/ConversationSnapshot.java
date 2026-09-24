package com.loanops.conversation;

import java.util.List;

/**
 * 一次 Agent 请求开始时读取到的会话快照，用来把“当时看到的历史和写入位置”一起传给后续流程。
 * Agent 使用 {@link #history()} 理解上下文，但当前贷款事实仍需重新调用 Tool 查询。
 * 请求完成后，{@link #version()} 和 {@link #lastMessageSequence()} 会作为 CAS
 * （比较并交换，用于乐观并发控制）的预期值：只有会话仍处于该快照状态时，才能追加本轮消息，
 * 从而避免并发请求相互覆盖。
 *
 * @param conversationId 会话的唯一标识
 * @param version 会话版本号；每成功提交一轮就递增，用于发现读取快照后发生的并发更新
 * @param lastMessageSequence 快照时最后一条已持久化消息的序号，也用于为下一组 USER / ASSISTANT 消息分配连续序号
 * @param history 提供给本轮 Agent 的最近完整对话轮次，已受消息数和字符数上限约束
 * @param historyHash 当前 {@code history} 的 SHA-256 指纹，供 Agent Audit 记录本轮实际使用的历史
 */
public record ConversationSnapshot(
        String conversationId,
        long version,
        int lastMessageSequence,
        List<ConversationHistoryMessage> history,
        String historyHash) {

    /**
     * 创建快照时复制历史列表，避免调用方随后修改原列表而改变快照内容。
     */
    public ConversationSnapshot {
        history = List.copyOf(history);
    }
}
