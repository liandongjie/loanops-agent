package com.loanops.conversation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loanops.exception.ConversationConflictException;
import com.loanops.exception.ConversationNotFoundException;
import com.loanops.persistence.entity.AgentAuditLogEntity;
import com.loanops.persistence.entity.ConversationEntity;
import com.loanops.persistence.entity.ConversationMessageEntity;
import com.loanops.persistence.mapper.AgentAuditLogMapper;
import com.loanops.persistence.mapper.ConversationMapper;
import com.loanops.persistence.mapper.ConversationMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "loanops.conversation.history-message-limit=3")
class ConversationTurnStoreIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 5, 10, 0);

    @Autowired
    private ConversationTurnStore store;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationMessageMapper messageMapper;

    @Autowired
    private AgentAuditLogMapper auditMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanConversationData() {
        jdbcTemplate.update("DELETE FROM agent_tool_audit_log");
        jdbcTemplate.update("DELETE FROM conversation_message");
        jdbcTemplate.update("DELETE FROM agent_audit_log");
        jdbcTemplate.update("DELETE FROM conversation");
    }

    @Test
    void newConversationStartsAtVersionAndSequenceZero() {
        ConversationSnapshot created = store.create();

        assertThat(created.conversationId()).isNotBlank();
        assertThat(created.version()).isZero();
        assertThat(created.lastMessageSequence()).isZero();
        assertThat(created.history()).isEmpty();
        assertThat(created.historyHash())
                .isEqualTo(ConversationHistoryFingerprint.sha256(List.of()));
    }

    @Test
    void resolveReturnsExistingConversation() {
        ConversationSnapshot created = store.create();

        ConversationSnapshot resolved = store.resolve(created.conversationId());

        assertThat(resolved).isEqualTo(created);
    }

    @Test
    void resolveRejectsUnknownConversation() {
        String conversationId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> store.resolve(conversationId))
                .isInstanceOf(ConversationNotFoundException.class)
                .hasMessageContaining(conversationId);
    }

    @Test
    void successfulTurnAppendsUserThenAssistantAndAdvancesState() {
        ConversationSnapshot created = store.create();
        String requestId = insertAudit(created.conversationId());

        store.appendSuccessfulTurn(created.conversationId(), 0, 0, requestId, "question", "answer");

        ConversationSnapshot resolved = store.resolve(created.conversationId());
        assertThat(resolved.version()).isEqualTo(1);
        assertThat(resolved.lastMessageSequence()).isEqualTo(2);
        assertThat(resolved.history()).extracting(ConversationHistoryMessage::sequenceNo)
                .containsExactly(1, 2);
        assertThat(resolved.history()).extracting(ConversationHistoryMessage::role)
                .containsExactly("USER", "ASSISTANT");
        assertThat(resolved.history()).extracting(ConversationHistoryMessage::content)
                .containsExactly("question", "answer");
    }

    @Test
    void boundedHistoryIsChronologicalAndKeepsCompleteTurns() {
        ConversationSnapshot created = store.create();
        appendTurn(created.conversationId(), 0, 0, "question-1", "answer-1");
        appendTurn(created.conversationId(), 1, 2, "question-2", "answer-2");
        appendTurn(created.conversationId(), 2, 4, "question-3", "answer-3");

        ConversationSnapshot resolved = store.resolve(created.conversationId());

        assertThat(resolved.version()).isEqualTo(3);
        assertThat(resolved.lastMessageSequence()).isEqualTo(6);
        assertThat(resolved.history()).extracting(ConversationHistoryMessage::sequenceNo)
                .containsExactly(5, 6);
        assertThat(resolved.history()).extracting(ConversationHistoryMessage::role)
                .containsExactly("USER", "ASSISTANT");
        assertThat(resolved.history()).extracting(ConversationHistoryMessage::content)
                .containsExactly("question-3", "answer-3");
    }

    @Test
    void historyIsIsolatedByConversation() {
        ConversationSnapshot first = store.create();
        ConversationSnapshot second = store.create();
        appendTurn(first.conversationId(), 0, 0, "first-question", "first-answer");
        appendTurn(second.conversationId(), 0, 0, "second-question", "second-answer");

        assertThat(store.resolve(first.conversationId()).history())
                .extracting(ConversationHistoryMessage::content)
                .containsExactly("first-question", "first-answer");
        assertThat(store.resolve(second.conversationId()).history())
                .extracting(ConversationHistoryMessage::content)
                .containsExactly("second-question", "second-answer");
    }

    @Test
    void snapshotExcludesNonConversationRoles() {
        ConversationSnapshot created = store.create();
        String requestId = insertAudit(created.conversationId());
        assertThat(messageMapper.insert(message(
                created.conversationId(), requestId, 1, "SYSTEM", "internal prompt"))).isEqualTo(1);

        assertThat(store.resolve(created.conversationId()).history()).isEmpty();
    }

    @Test
    void persistenceFailureRollsBackAdvanceAndFirstMessage() {
        ConversationSnapshot created = store.create();
        String requestId = insertAudit(created.conversationId());
        assertThat(messageMapper.insert(message(
                created.conversationId(), requestId, 99, "ASSISTANT", "existing"))).isEqualTo(1);

        assertThatThrownBy(() -> store.appendSuccessfulTurn(
                created.conversationId(), 0, 0, requestId, "question", "answer"))
                .isInstanceOf(DataIntegrityViolationException.class);

        ConversationEntity conversation = conversationMapper.selectById(created.conversationId());
        assertThat(conversation.getVersion()).isZero();
        assertThat(conversation.getLastMessageSequence()).isZero();
        List<ConversationMessageEntity> messages = messages(created.conversationId());
        assertThat(messages).extracting(ConversationMessageEntity::getRole)
                .containsExactly("ASSISTANT");
        assertThat(messages).extracting(ConversationMessageEntity::getSequenceNo)
                .containsExactly(99);
    }

    @Test
    void concurrentCompletionsWithSameExpectedStateYieldOneSuccessAndOneConflict() throws Exception {
        ConversationSnapshot created = store.create();
        String firstRequestId = insertAudit(created.conversationId());
        String secondRequestId = insertAudit(created.conversationId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Throwable> first = executor.submit(() -> completeAfterSignal(
                    ready, start, created.conversationId(), firstRequestId, "question-1", "answer-1"));
            Future<Throwable> second = executor.submit(() -> completeAfterSignal(
                    ready, start, created.conversationId(), secondRequestId, "question-2", "answer-2"));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Throwable> outcomes = Arrays.asList(
                    first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(Objects::isNull).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(ConversationConflictException.class::isInstance).count())
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        ConversationSnapshot resolved = store.resolve(created.conversationId());
        assertThat(resolved.version()).isEqualTo(1);
        assertThat(resolved.lastMessageSequence()).isEqualTo(2);
        assertThat(resolved.history()).extracting(ConversationHistoryMessage::role)
                .containsExactly("USER", "ASSISTANT");
        assertThat(messages(created.conversationId())).hasSize(2);
    }

    private Throwable completeAfterSignal(
            CountDownLatch ready,
            CountDownLatch start,
            String conversationId,
            String requestId,
            String userContent,
            String assistantContent) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            store.appendSuccessfulTurn(conversationId, 0, 0, requestId, userContent, assistantContent);
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private void appendTurn(
            String conversationId,
            long expectedVersion,
            int expectedLastMessageSequence,
            String userContent,
            String assistantContent) {
        store.appendSuccessfulTurn(
                conversationId,
                expectedVersion,
                expectedLastMessageSequence,
                insertAudit(conversationId),
                userContent,
                assistantContent);
    }

    private String insertAudit(String conversationId) {
        String requestId = UUID.randomUUID().toString();
        AgentAuditLogEntity audit = new AgentAuditLogEntity();
        audit.setRequestId(requestId);
        audit.setConversationId(conversationId);
        audit.setStartedAt(NOW);
        audit.setStatus("STARTED");
        audit.setBusinessDate(LocalDate.of(2026, 9, 5));
        audit.setProvider("test");
        audit.setModel("test-model");
        audit.setMessageLength(8);
        audit.setMessageHash("a".repeat(64));
        assertThat(auditMapper.insert(audit)).isEqualTo(1);
        return requestId;
    }

    private ConversationMessageEntity message(
            String conversationId,
            String requestId,
            int sequenceNo,
            String role,
            String content) {
        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setMessageId(UUID.randomUUID().toString());
        message.setConversationId(conversationId);
        message.setRequestId(requestId);
        message.setSequenceNo(sequenceNo);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(NOW);
        return message;
    }

    private List<ConversationMessageEntity> messages(String conversationId) {
        return messageMapper.selectList(new LambdaQueryWrapper<ConversationMessageEntity>()
                .eq(ConversationMessageEntity::getConversationId, conversationId)
                .orderByAsc(ConversationMessageEntity::getSequenceNo));
    }
}
