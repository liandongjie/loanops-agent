package com.loanops.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loanops.persistence.entity.AgentAuditLogEntity;
import com.loanops.persistence.entity.ConversationEntity;
import com.loanops.persistence.entity.ConversationMessageEntity;
import com.loanops.persistence.mapper.AgentAuditLogMapper;
import com.loanops.persistence.mapper.ConversationMapper;
import com.loanops.persistence.mapper.ConversationMessageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ConversationPersistenceIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 6, 0);

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationMessageMapper messageMapper;

    @Autowired
    private AgentAuditLogMapper auditMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mappersPersistConversationMessagesAndAuditCorrelation() {
        String conversationId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        insertConversation(conversationId);

        AgentAuditLogEntity audit = audit(requestId, conversationId);
        audit.setHistoryFromSequence(1);
        audit.setHistoryToSequence(2);
        audit.setHistoryHash("a".repeat(64));
        audit.setSystemPromptHash("b".repeat(64));
        assertThat(auditMapper.insert(audit)).isEqualTo(1);

        assertThat(messageMapper.insert(message(
                UUID.randomUUID().toString(), conversationId, requestId, 1, "USER", "为什么逾期？")))
                .isEqualTo(1);
        assertThat(messageMapper.insert(message(
                UUID.randomUUID().toString(), conversationId, requestId, 2, "ASSISTANT", "逾期 3 天。")))
                .isEqualTo(1);

        ConversationEntity storedConversation = conversationMapper.selectById(conversationId);
        assertThat(storedConversation.getVersion()).isZero();
        assertThat(storedConversation.getLastMessageSequence()).isZero();

        AgentAuditLogEntity storedAudit = auditMapper.selectById(requestId);
        assertThat(storedAudit.getConversationId()).isEqualTo(conversationId);
        assertThat(storedAudit.getHistoryFromSequence()).isEqualTo(1);
        assertThat(storedAudit.getHistoryToSequence()).isEqualTo(2);
        assertThat(storedAudit.getHistoryHash()).isEqualTo("a".repeat(64));
        assertThat(storedAudit.getSystemPromptHash()).isEqualTo("b".repeat(64));

        List<ConversationMessageEntity> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessageEntity>()
                        .eq(ConversationMessageEntity::getConversationId, conversationId)
                        .orderByAsc(ConversationMessageEntity::getSequenceNo));
        assertThat(messages).extracting(ConversationMessageEntity::getRole)
                .containsExactly("USER", "ASSISTANT");
        assertThat(messages).extracting(ConversationMessageEntity::getContent)
                .containsExactly("为什么逾期？", "逾期 3 天。");
    }

    @Test
    void duplicateSequenceWithinConversationIsRejected() {
        String conversationId = UUID.randomUUID().toString();
        String firstRequestId = UUID.randomUUID().toString();
        String secondRequestId = UUID.randomUUID().toString();
        insertConversation(conversationId);
        assertThat(auditMapper.insert(audit(firstRequestId, conversationId))).isEqualTo(1);
        assertThat(auditMapper.insert(audit(secondRequestId, conversationId))).isEqualTo(1);
        assertThat(messageMapper.insert(message(
                UUID.randomUUID().toString(), conversationId, firstRequestId, 1, "USER", "first")))
                .isEqualTo(1);

        assertThatThrownBy(() -> messageMapper.insert(message(
                UUID.randomUUID().toString(), conversationId, secondRequestId, 1, "USER", "duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateRoleWithinRequestIsRejected() {
        String conversationId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        insertConversation(conversationId);
        assertThat(auditMapper.insert(audit(requestId, conversationId))).isEqualTo(1);
        assertThat(messageMapper.insert(message(
                UUID.randomUUID().toString(), conversationId, requestId, 1, "USER", "first")))
                .isEqualTo(1);

        assertThatThrownBy(() -> messageMapper.insert(message(
                UUID.randomUUID().toString(), conversationId, requestId, 2, "USER", "duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void messageRequiresExistingConversationAndAuditRequest() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO conversation_message (
                    message_id, conversation_id, request_id, sequence_no, role, content, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 1, "USER", "orphan", NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertConversation(String conversationId) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setConversationId(conversationId);
        conversation.setCreatedAt(NOW);
        conversation.setUpdatedAt(NOW);
        assertThat(conversationMapper.insert(conversation)).isEqualTo(1);
    }

    private AgentAuditLogEntity audit(String requestId, String conversationId) {
        AgentAuditLogEntity audit = new AgentAuditLogEntity();
        audit.setRequestId(requestId);
        audit.setConversationId(conversationId);
        audit.setStartedAt(NOW);
        audit.setStatus("STARTED");
        audit.setBusinessDate(LocalDate.of(2026, 8, 24));
        audit.setProvider("test");
        audit.setModel("test-model");
        audit.setMessageLength(4);
        audit.setMessageHash("c".repeat(64));
        return audit;
    }

    private ConversationMessageEntity message(
            String messageId,
            String conversationId,
            String requestId,
            int sequenceNo,
            String role,
            String content) {
        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setMessageId(messageId);
        message.setConversationId(conversationId);
        message.setRequestId(requestId);
        message.setSequenceNo(sequenceNo);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(NOW);
        return message;
    }
}
