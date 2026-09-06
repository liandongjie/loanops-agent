CREATE TABLE policy_document (
    document_id CHAR(36) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    issuer VARCHAR(255) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    jurisdiction VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE policy_document_version (
    version_id CHAR(36) PRIMARY KEY,
    document_id CHAR(36) NOT NULL,
    version_label VARCHAR(128) NOT NULL,
    document_number VARCHAR(255),
    issued_at DATE,
    published_at DATE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(32) NOT NULL,
    source_uri VARCHAR(1000),
    content_hash CHAR(64) NOT NULL,
    supersedes_version_id CHAR(36),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_policy_version_document
        FOREIGN KEY (document_id) REFERENCES policy_document(document_id),
    CONSTRAINT fk_policy_version_supersedes
        FOREIGN KEY (supersedes_version_id) REFERENCES policy_document_version(version_id),
    CONSTRAINT ck_policy_version_dates
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX idx_policy_version_applicability
    ON policy_document_version(status, effective_from, effective_to);

CREATE TABLE policy_chunk (
    chunk_id CHAR(36) PRIMARY KEY,
    version_id CHAR(36) NOT NULL,
    parent_chunk_id CHAR(36),
    chunk_type VARCHAR(32) NOT NULL,
    chapter_no VARCHAR(64),
    chapter_title VARCHAR(255),
    article_no VARCHAR(64) NOT NULL,
    paragraph_no INT,
    item_no VARCHAR(64),
    section_path VARCHAR(1000) NOT NULL,
    ordinal INT NOT NULL,
    content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    retrieval_enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_policy_chunk_ordinal UNIQUE (version_id, ordinal),
    CONSTRAINT fk_policy_chunk_version
        FOREIGN KEY (version_id) REFERENCES policy_document_version(version_id),
    CONSTRAINT fk_policy_chunk_parent
        FOREIGN KEY (parent_chunk_id) REFERENCES policy_chunk(chunk_id)
);

CREATE INDEX idx_policy_chunk_reference
    ON policy_chunk(version_id, article_no, paragraph_no, item_no);
