package com.loanops.agent;

import com.loanops.audit.AgentAuditHandle;
import com.loanops.audit.AgentAuditService;
import com.loanops.conversation.ConversationSnapshot;
import com.loanops.conversation.ConversationTurnStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentTurnCompletionService {

    private final ConversationTurnStore turnStore;
    private final AgentAuditService auditService;

    public AgentTurnCompletionService(ConversationTurnStore turnStore, AgentAuditService auditService) {
        this.turnStore = turnStore;
        this.auditService = auditService;
    }

    @Transactional
    public long complete(
            AgentAuditHandle auditHandle,
            ConversationSnapshot snapshot,
            String userMessage,
            String assistantMessage) {
        turnStore.appendSuccessfulTurn(
                snapshot.conversationId(),
                snapshot.version(),
                snapshot.lastMessageSequence(),
                auditHandle.requestId(),
                userMessage,
                assistantMessage);
        return auditService.completeSuccess(auditHandle, assistantMessage);
    }
}
