CREATE TABLE policy_retrieval_audit (
    retrieval_id CHAR(36) PRIMARY KEY,
    request_id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    as_of_date DATE NOT NULL,
    query_hash CHAR(64),
    status VARCHAR(16) NOT NULL,
    top_k INT NOT NULL,
    score_threshold DOUBLE NOT NULL,
    retrieval_config_hash CHAR(64) NOT NULL,
    embedding_model VARCHAR(128) NOT NULL,
    index_collection VARCHAR(128) NOT NULL,
    context_hash CHAR(64),
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    duration_ms BIGINT,
    error_type VARCHAR(128),
    CONSTRAINT uq_policy_retrieval_request UNIQUE (request_id),
    CONSTRAINT fk_policy_retrieval_request
        FOREIGN KEY (request_id) REFERENCES agent_audit_log(request_id) ON DELETE CASCADE,
    CONSTRAINT fk_policy_retrieval_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation(conversation_id)
);

CREATE TABLE policy_retrieval_hit (
    retrieval_id CHAR(36) NOT NULL,
    rank_no INT NOT NULL,
    document_id CHAR(36) NOT NULL,
    version_id CHAR(36) NOT NULL,
    chunk_id CHAR(36) NOT NULL,
    match_type VARCHAR(32) NOT NULL,
    score DOUBLE NOT NULL,
    selected_for_context BOOLEAN NOT NULL,
    citation_ref VARCHAR(16),
    cited_in_answer BOOLEAN NOT NULL,
    PRIMARY KEY (retrieval_id, rank_no),
    CONSTRAINT fk_policy_retrieval_hit
        FOREIGN KEY (retrieval_id) REFERENCES policy_retrieval_audit(retrieval_id) ON DELETE CASCADE
);

CREATE INDEX idx_policy_retrieval_conversation_started
    ON policy_retrieval_audit(conversation_id, started_at);

CREATE INDEX idx_policy_retrieval_hit_chunk
    ON policy_retrieval_hit(chunk_id);
