CREATE TABLE conversation (
    conversation_id CHAR(36) PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    last_message_sequence INT NOT NULL DEFAULT 0
);

ALTER TABLE agent_audit_log ADD COLUMN conversation_id CHAR(36);
ALTER TABLE agent_audit_log ADD COLUMN history_from_sequence INT;
ALTER TABLE agent_audit_log ADD COLUMN history_to_sequence INT;
ALTER TABLE agent_audit_log ADD COLUMN history_hash CHAR(64);
ALTER TABLE agent_audit_log ADD COLUMN system_prompt_hash CHAR(64);

ALTER TABLE agent_audit_log
    ADD CONSTRAINT fk_agent_audit_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation(conversation_id);

CREATE INDEX idx_agent_audit_conversation_started
    ON agent_audit_log(conversation_id, started_at);

CREATE TABLE conversation_message (
    message_id CHAR(36) PRIMARY KEY,
    conversation_id CHAR(36) NOT NULL,
    request_id CHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_conversation_message_sequence
        UNIQUE (conversation_id, sequence_no),
    CONSTRAINT uq_conversation_message_request_role
        UNIQUE (request_id, role),
    CONSTRAINT fk_conversation_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation(conversation_id),
    CONSTRAINT fk_conversation_message_request
        FOREIGN KEY (request_id) REFERENCES agent_audit_log(request_id)
);
