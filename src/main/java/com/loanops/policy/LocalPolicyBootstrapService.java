package com.loanops.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanops.policy.PolicyTypes.DocumentInput;
import com.loanops.policy.PolicyTypes.VersionInput;
import com.loanops.policy.PolicyTypes.VersionStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Profile("local-policy-bootstrap")
public class LocalPolicyBootstrapService {

    static final String CORPUS_IDENTIFIER = "policy-rag-eval-corpus-v1";
    static final String CORPUS_SOURCE_TYPE = "EVALUATION_SYNTHETIC";
    static final String LOCAL_DEMO_SOURCE_TYPE = "LOCAL_DEMO_SYNTHETIC";

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final PolicyIngestionService ingestionService;
    private final PolicyIndexRebuilder indexRebuilder;

    public LocalPolicyBootstrapService(ObjectMapper objectMapper, JdbcTemplate jdbc,
                                       PolicyIngestionService ingestionService,
                                       PolicyIndexRebuilder indexRebuilder) {
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.ingestionService = ingestionService;
        this.indexRebuilder = indexRebuilder;
    }

    public BootstrapResult bootstrap(Resource corpusResource) {
        List<CorpusEntry> entries = readAndValidate(corpusResource);
        assertExistingStoreIsSafe(entries);
        try {
            entries.forEach(entry -> ingestionService.ingest(entry.document(), entry.version()));
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "Local demo policy ingestion failed; check MySQL connectivity and the policy schema", failure);
        }

        int indexed;
        try {
            indexed = indexRebuilder.rebuild();
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "Local demo policy index rebuild failed; check Ollama, bge-m3, Qdrant, and the configured policy collection",
                    failure);
        }
        return new BootstrapResult(count("policy_document"), count("policy_document_version"),
                count("policy_chunk"), indexed);
    }

    private List<CorpusEntry> readAndValidate(Resource resource) {
        DemoCorpus corpus;
        try (var input = resource.getInputStream()) {
            corpus = objectMapper.readValue(input, DemoCorpus.class);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException(
                    "Local demo policy corpus is malformed or unreadable; check " + resource.getDescription(), failure);
        }
        if (!CORPUS_IDENTIFIER.equals(corpus.identifier())
                || !CORPUS_SOURCE_TYPE.equals(corpus.sourceType())
                || corpus.documents() == null || corpus.documents().isEmpty()) {
            throw new IllegalStateException("Local demo policy corpus has an unexpected identity or no documents");
        }
        try {
            return corpus.documents().stream().flatMap(document -> {
                requireText(document.title(), "document title");
                requireText(document.documentType(), "document type");
                requireText(document.issuer(), "document issuer");
                requireText(document.jurisdiction(), "document jurisdiction");
                if (document.versions() == null || document.versions().isEmpty()) {
                    throw new IllegalArgumentException("document versions must not be empty");
                }
                DocumentInput input = new DocumentInput(document.title(), document.documentType(),
                        document.issuer(), LOCAL_DEMO_SOURCE_TYPE, document.jurisdiction());
                return document.versions().stream().map(version -> entry(input, version));
            }).toList();
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Local demo policy corpus is malformed; validate its metadata and dates", failure);
        }
    }

    private void assertExistingStoreIsSafe(List<CorpusEntry> entries) {
        Set<DocumentFingerprint> allowedDocuments = new LinkedHashSet<>();
        entries.forEach(entry -> allowedDocuments.add(DocumentFingerprint.from(entry.document())));
        Set<VersionFingerprint> allowedVersions = entries.stream()
                .map(VersionFingerprint::from).collect(java.util.stream.Collectors.toSet());
        try {
            List<DocumentFingerprint> existing = jdbc.query("""
                    SELECT title, document_type, issuer, source_type, jurisdiction
                    FROM policy_document
                    """, (rs, rowNum) -> new DocumentFingerprint(
                    rs.getString("title"), rs.getString("document_type"), rs.getString("issuer"),
                    rs.getString("source_type"), rs.getString("jurisdiction")));
            long unknown = existing.stream().filter(document -> !allowedDocuments.contains(document)).count();
            if (unknown > 0) {
                throw new IllegalStateException("Local demo policy bootstrap refused: found " + unknown
                        + " non-demo or unknown policy document(s); no data or Qdrant index was changed");
            }
            List<VersionFingerprint> existingVersions = jdbc.query("""
                    SELECT d.title, v.version_label, v.document_number, v.issued_at, v.published_at,
                           v.effective_from, v.effective_to, v.status, v.source_uri, v.content_hash
                    FROM policy_document_version v
                    JOIN policy_document d ON d.document_id = v.document_id
                    """, (rs, rowNum) -> new VersionFingerprint(
                    rs.getString("title"), rs.getString("version_label"), rs.getString("document_number"),
                    rs.getDate("issued_at") == null ? null : rs.getDate("issued_at").toLocalDate(),
                    rs.getDate("published_at") == null ? null : rs.getDate("published_at").toLocalDate(),
                    rs.getDate("effective_from").toLocalDate(),
                    rs.getDate("effective_to") == null ? null : rs.getDate("effective_to").toLocalDate(),
                    rs.getString("status"), rs.getString("source_uri"), rs.getString("content_hash")));
            long unknownVersions = existingVersions.stream()
                    .filter(version -> !allowedVersions.contains(version)).count();
            if (unknownVersions > 0) {
                throw new IllegalStateException("Local demo policy bootstrap refused: found " + unknownVersions
                        + " non-demo or unknown policy version(s); no data or Qdrant index was changed");
            }
        } catch (DataAccessException failure) {
            throw new IllegalStateException(
                    "Local policy store safety check failed; check MySQL connectivity and Flyway policy schema", failure);
        }
    }

    private CorpusEntry entry(DocumentInput document, DemoVersion version) {
        requireText(version.versionLabel(), "version label");
        requireText(version.documentNumber(), "document number");
        requireText(version.effectiveFrom(), "effective from");
        requireText(version.sourceUri(), "source URI");
        requireText(version.structuredText(), "structured text");
        LocalDate from = LocalDate.parse(version.effectiveFrom());
        LocalDate to = version.effectiveTo() == null ? null : LocalDate.parse(version.effectiveTo());
        return new CorpusEntry(document, new VersionInput(version.versionLabel(), version.documentNumber(),
                from.minusDays(30), from.minusDays(15), from, to, VersionStatus.ACTIVE,
                version.sourceUri(), null, version.structuredText()));
    }

    private int count(String table) {
        Integer result = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return result == null ? 0 : result;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    public record BootstrapResult(int documentCount, int versionCount, int chunkCount, int indexedCount) {}

    private record CorpusEntry(DocumentInput document, VersionInput version) {}
    private record DocumentFingerprint(String title, String documentType, String issuer,
                                       String sourceType, String jurisdiction) {
        static DocumentFingerprint from(DocumentInput input) {
            return new DocumentFingerprint(input.title(), input.documentType(), input.issuer(),
                    input.sourceType(), input.jurisdiction());
        }
    }
    private record VersionFingerprint(String title, String versionLabel, String documentNumber,
                                      LocalDate issuedAt, LocalDate publishedAt, LocalDate effectiveFrom,
                                      LocalDate effectiveTo, String status, String sourceUri, String contentHash) {
        static VersionFingerprint from(CorpusEntry entry) {
            VersionInput version = entry.version();
            return new VersionFingerprint(entry.document().title(), version.versionLabel(),
                    version.documentNumber(), version.issuedAt(), version.publishedAt(), version.effectiveFrom(),
                    version.effectiveTo(), version.status().name(), version.sourceUri(),
                    PolicyHashing.sha256(PolicyHashing.normalize(version.structuredText())));
        }
    }
    private record DemoCorpus(String identifier, String sourceType, List<DemoDocument> documents) {}
    private record DemoDocument(String title, String documentType, String issuer, String jurisdiction,
                                List<DemoVersion> versions) {}
    private record DemoVersion(String versionLabel, String documentNumber, String effectiveFrom,
                               String effectiveTo, String sourceUri, String structuredText) {}
}
