package com.loanops.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LoanNotFoundException.class)
    public ResponseEntity<ApiError> handleLoanNotFound(LoanNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), "LOAN_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(AgentAuditNotFoundException.class)
    public ResponseEntity<ApiError> handleAgentAuditNotFound(AgentAuditNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), "AGENT_AUDIT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ApiError> handleConversationNotFound(ConversationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), "CONVERSATION_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(ConversationConflictException.class)
    public ResponseEntity<ApiError> handleConversationConflict(ConversationConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(HttpStatus.CONFLICT.value(), "CONVERSATION_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(InvalidAgentMessageException.class)
    public ResponseEntity<ApiError> handleInvalidAgentMessage(InvalidAgentMessageException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), "INVALID_AGENT_MESSAGE", exception.getMessage()));
    }

    @ExceptionHandler(AgentProviderUnavailableException.class)
    public ResponseEntity<ApiError> handleAgentProviderUnavailable(AgentProviderUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(HttpStatus.SERVICE_UNAVAILABLE.value(), "AGENT_PROVIDER_UNAVAILABLE",
                        exception.getMessage()));
    }

    @ExceptionHandler(PolicyRetrievalException.class)
    public ResponseEntity<ApiError> handlePolicyRetrieval(PolicyRetrievalException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(HttpStatus.SERVICE_UNAVAILABLE.value(), "POLICY_RETRIEVAL_UNAVAILABLE",
                        "Required policy retrieval is temporarily unavailable"));
    }
}
