package com.loanops.conversation;

import com.loanops.audit.AuditTimeProvider;
import com.loanops.exception.ConversationConflictException;
import com.loanops.exception.ConversationNotFoundException;
import com.loanops.persistence.entity.ConversationEntity;
import com.loanops.persistence.entity.ConversationMessageEntity;
import com.loanops.persistence.mapper.ConversationMapper;
import com.loanops.persistence.mapper.ConversationMessageMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ConversationTurnStore {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final AuditTimeProvider timeProvider;
    private final int historyMessageLimit;
    private final int historyCharacterLimit;

    public ConversationTurnStore(
            ConversationMapper conversationMapper,
            ConversationMessageMapper messageMapper,
            AuditTimeProvider timeProvider,
            @Value("${loanops.conversation.history-message-limit:20}") int historyMessageLimit,
            @Value("${loanops.conversation.history-character-limit:12000}") int historyCharacterLimit) {
        if (historyMessageLimit < 2) {
            throw new IllegalArgumentException("Conversation history message limit must be at least 2");
        }
        if (historyCharacterLimit < 1) {
            throw new IllegalArgumentException("Conversation history character limit must be positive");
        }
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.timeProvider = timeProvider;
        this.historyMessageLimit = historyMessageLimit - historyMessageLimit % 2;
        this.historyCharacterLimit = historyCharacterLimit;
    }

    @Transactional
    public ConversationSnapshot create() {
        LocalDateTime now = timeProvider.nowUtc();
        ConversationEntity conversation = new ConversationEntity();
        conversation.setConversationId(UUID.randomUUID().toString());
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversation.setVersion(0L);
        conversation.setLastMessageSequence(0);
        if (conversationMapper.insert(conversation) != 1) {
            throw new IllegalStateException("conversation insert affected no rows");
        }
        return snapshot(conversation, List.of());
    }

    @Transactional(readOnly = true)
    public ConversationSnapshot resolve(String conversationId) {
        ConversationEntity conversation = requireConversation(conversationId);
        List<ConversationMessageEntity> recent = messageMapper.selectRecentHistory(
                conversationId, historyMessageLimit);
        Collections.reverse(recent);
        List<ConversationHistoryMessage> history = recent.stream()
                .map(message -> new ConversationHistoryMessage(
                        message.getSequenceNo(), message.getRole(), message.getContent()))
                .toList();
        return snapshot(conversation, retainNewestCompleteTurns(history));
    }

    @Transactional
    public void appendSuccessfulTurn(
            String conversationId,
            long expectedVersion,
            int expectedLastMessageSequence,
            String requestId,
            String userContent,
            String assistantContent) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(userContent, "userContent");
        Objects.requireNonNull(assistantContent, "assistantContent");

        LocalDateTime now = timeProvider.nowUtc();
        int updated = conversationMapper.advance(
                conversationId, expectedVersion, expectedLastMessageSequence, now);
        if (updated != 1) {
            if (conversationMapper.selectById(conversationId) == null) {
                throw new ConversationNotFoundException(conversationId);
            }
            throw new ConversationConflictException(conversationId, expectedVersion, expectedLastMessageSequence);
        }

        insertMessage(conversationId, requestId, expectedLastMessageSequence + 1, "USER", userContent, now);
        insertMessage(conversationId, requestId, expectedLastMessageSequence + 2, "ASSISTANT", assistantContent, now);
    }

    private ConversationEntity requireConversation(String conversationId) {
        ConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new ConversationNotFoundException(conversationId);
        }
        return conversation;
    }

    private void insertMessage(
            String conversationId,
            String requestId,
            int sequenceNo,
            String role,
            String content,
            LocalDateTime createdAt) {
        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setMessageId(UUID.randomUUID().toString());
        message.setConversationId(conversationId);
        message.setRequestId(requestId);
        message.setSequenceNo(sequenceNo);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(createdAt);
        if (messageMapper.insert(message) != 1) {
            throw new IllegalStateException("conversation message insert affected no rows");
        }
    }

    private ConversationSnapshot snapshot(
            ConversationEntity conversation,
            List<ConversationHistoryMessage> history) {
        return new ConversationSnapshot(
                conversation.getConversationId(),
                conversation.getVersion(),
                conversation.getLastMessageSequence(),
                history,
                ConversationHistoryFingerprint.sha256(history));
    }

    private List<ConversationHistoryMessage> retainNewestCompleteTurns(List<ConversationHistoryMessage> history) {
        int characters = 0;
        int fromIndex = history.size();
        for (int index = history.size() - 2; index >= 0; index -= 2) {
            ConversationHistoryMessage user = history.get(index);
            ConversationHistoryMessage assistant = history.get(index + 1);
            int turnCharacters = user.content().length() + assistant.content().length();
            if (characters + turnCharacters > historyCharacterLimit) break;
            characters += turnCharacters;
            fromIndex = index;
        }
        return history.subList(fromIndex, history.size());
    }
}
