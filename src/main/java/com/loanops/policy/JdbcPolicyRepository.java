package com.loanops.policy;

import com.loanops.policy.PolicyTypes.Chunk;
import com.loanops.policy.PolicyTypes.ChunkType;
import com.loanops.policy.PolicyTypes.Document;
import com.loanops.policy.PolicyTypes.StoredChunk;
import com.loanops.policy.PolicyTypes.Version;
import com.loanops.policy.PolicyTypes.VersionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPolicyRepository implements PolicyRepository {

    private static final String SELECT_STORED = """
            SELECT c.chunk_id, c.version_id, c.parent_chunk_id, c.chunk_type,
                   c.chapter_no, c.chapter_title, c.article_no, c.paragraph_no, c.item_no,
                   c.section_path, c.ordinal, c.content, c.content_hash AS chunk_content_hash,
                   c.retrieval_enabled, c.created_at AS chunk_created_at,
                   d.document_id, d.title, d.document_type, d.issuer, d.source_type,
                   d.jurisdiction, d.created_at AS document_created_at, d.updated_at,
                   v.version_label, v.document_number, v.issued_at, v.published_at,
                   v.effective_from, v.effective_to, v.status, v.source_uri,
                   v.content_hash AS version_content_hash, v.supersedes_version_id,
                   v.created_at AS version_created_at
            FROM policy_chunk c
            JOIN policy_document_version v ON v.version_id = c.version_id
            JOIN policy_document d ON d.document_id = v.document_id
            """;

    private final JdbcTemplate jdbc;
    private final RowMapper<StoredChunk> rowMapper = this::mapStoredChunk;

    public JdbcPolicyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertDocumentIfAbsent(Document document) {
        if (count("policy_document", "document_id", document.documentId()) > 0) return;
        jdbc.update("""
                INSERT INTO policy_document (
                    document_id, title, document_type, issuer, source_type, jurisdiction, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, document.documentId(), document.title(), document.documentType(), document.issuer(),
                document.sourceType(), document.jurisdiction(), document.createdAt(), document.updatedAt());
    }

    @Override
    public void insertVersionIfAbsent(Version version) {
        if (count("policy_document_version", "version_id", version.versionId()) > 0) return;
        jdbc.update("""
                INSERT INTO policy_document_version (
                    version_id, document_id, version_label, document_number, issued_at, published_at,
                    effective_from, effective_to, status, source_uri, content_hash,
                    supersedes_version_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, version.versionId(), version.documentId(), version.versionLabel(), version.documentNumber(),
                version.issuedAt(), version.publishedAt(), version.effectiveFrom(), version.effectiveTo(),
                version.status().name(), version.sourceUri(), version.contentHash(),
                version.supersedesVersionId(), version.createdAt());
    }

    @Override
    public void insertChunkIfAbsent(Chunk chunk) {
        if (count("policy_chunk", "chunk_id", chunk.chunkId()) > 0) return;
        jdbc.update("""
                INSERT INTO policy_chunk (
                    chunk_id, version_id, parent_chunk_id, chunk_type, chapter_no, chapter_title,
                    article_no, paragraph_no, item_no, section_path, ordinal, content,
                    content_hash, retrieval_enabled, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, chunk.chunkId(), chunk.versionId(), chunk.parentChunkId(), chunk.chunkType().name(),
                chunk.chapterNo(), chunk.chapterTitle(), chunk.articleNo(), chunk.paragraphNo(), chunk.itemNo(),
                chunk.sectionPath(), chunk.ordinal(), chunk.content(), chunk.contentHash(),
                chunk.retrievalEnabled(), chunk.createdAt());
    }

    @Override
    public List<StoredChunk> findApplicable(LocalDate asOfDate) {
        return jdbc.query(SELECT_STORED + """
                WHERE c.retrieval_enabled = TRUE
                  AND v.status = 'ACTIVE'
                  AND v.effective_from <= ?
                  AND (v.effective_to IS NULL OR v.effective_to > ?)
                ORDER BY d.title, v.effective_from DESC, c.ordinal
                """, rowMapper, asOfDate, asOfDate);
    }

    @Override
    public Optional<StoredChunk> findChunk(String chunkId) {
        return jdbc.query(SELECT_STORED + " WHERE c.chunk_id = ?", rowMapper, chunkId)
                .stream().findFirst();
    }

    @Override
    public List<StoredChunk> findIndexableActiveChunks() {
        return jdbc.query(SELECT_STORED + """
                WHERE c.retrieval_enabled = TRUE AND v.status = 'ACTIVE'
                ORDER BY d.document_id, v.effective_from, c.ordinal
                """, rowMapper);
    }

    private int count(String table, String column, String id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    private StoredChunk mapStoredChunk(ResultSet rs, int rowNum) throws SQLException {
        Document document = new Document(
                rs.getString("document_id"), rs.getString("title"), rs.getString("document_type"),
                rs.getString("issuer"), rs.getString("source_type"), rs.getString("jurisdiction"),
                rs.getTimestamp("document_created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime());
        Version version = new Version(
                rs.getString("version_id"), document.documentId(), rs.getString("version_label"),
                rs.getString("document_number"), nullableDate(rs, "issued_at"), nullableDate(rs, "published_at"),
                rs.getDate("effective_from").toLocalDate(), nullableDate(rs, "effective_to"),
                VersionStatus.valueOf(rs.getString("status")), rs.getString("source_uri"),
                rs.getString("version_content_hash"), rs.getString("supersedes_version_id"),
                rs.getTimestamp("version_created_at").toLocalDateTime());
        Chunk chunk = new Chunk(
                rs.getString("chunk_id"), version.versionId(), rs.getString("parent_chunk_id"),
                ChunkType.valueOf(rs.getString("chunk_type")), rs.getString("chapter_no"),
                rs.getString("chapter_title"), rs.getString("article_no"),
                (Integer) rs.getObject("paragraph_no"), rs.getString("item_no"),
                rs.getString("section_path"), rs.getInt("ordinal"), rs.getString("content"),
                rs.getString("chunk_content_hash"), rs.getBoolean("retrieval_enabled"),
                rs.getTimestamp("chunk_created_at").toLocalDateTime());
        return new StoredChunk(chunk, document, version);
    }

    private static LocalDate nullableDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
