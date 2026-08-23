CREATE TABLE agent_audit_log (
    request_id CHAR(36) PRIMARY KEY,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    status VARCHAR(16) NOT NULL,
    duration_ms BIGINT,
    business_date DATE NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(128) NOT NULL,
    message_length INT NOT NULL,
    answer_length INT,
    message_hash CHAR(64) NOT NULL,
    answer_hash CHAR(64),
    message_text TEXT,
    answer_text TEXT,
    error_type VARCHAR(128)
);

CREATE TABLE agent_tool_audit_log (
    request_id CHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    loan_no VARCHAR(64),
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    status VARCHAR(16) NOT NULL,
    duration_ms BIGINT,
    error_type VARCHAR(128),
    PRIMARY KEY (request_id, sequence_no),
    CONSTRAINT fk_agent_tool_audit_request
        FOREIGN KEY (request_id) REFERENCES agent_audit_log(request_id)
);