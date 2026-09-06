package com.loanops.policy;

import com.loanops.policy.PolicyTypes.Chunk;
import com.loanops.policy.PolicyTypes.Document;
import com.loanops.policy.PolicyTypes.DocumentInput;
import com.loanops.policy.PolicyTypes.IngestionResult;
import com.loanops.policy.PolicyTypes.Version;
import com.loanops.policy.PolicyTypes.VersionInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class PolicyIngestionService {

    private final PolicyChunker chunker;
    private final PolicyRepository repository;
    private final Clock clock;

    public PolicyIngestionService(PolicyChunker chunker, PolicyRepository repository, Clock clock) {
        this.chunker = chunker;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public IngestionResult ingest(DocumentInput documentInput, VersionInput versionInput) {
        validate(documentInput, versionInput);
        LocalDateTime now = LocalDateTime.now(clock);
        String normalizedText = PolicyHashing.normalize(versionInput.structuredText());
        String contentHash = PolicyHashing.sha256(normalizedText);
        String documentIdentity = String.join("|", documentInput.title(), documentInput.documentType(),
                documentInput.issuer(), documentInput.sourceType(), documentInput.jurisdiction());
        String documentId = PolicyHashing.stableId("policy-document", documentIdentity);
        String versionIdentity = String.join("|", documentId, versionInput.versionLabel(),
                Objects.toString(versionInput.documentNumber(), ""),
                Objects.toString(versionInput.issuedAt(), ""), Objects.toString(versionInput.publishedAt(), ""),
                versionInput.effectiveFrom().toString(), Objects.toString(versionInput.effectiveTo(), ""),
                versionInput.status().name(), Objects.toString(versionInput.sourceUri(), ""),
                Objects.toString(versionInput.supersedesVersionId(), ""), contentHash);
        String versionId = PolicyHashing.stableId("policy-version", versionIdentity);

        Document document = new Document(documentId, documentInput.title(), documentInput.documentType(),
                documentInput.issuer(), documentInput.sourceType(), documentInput.jurisdiction(), now, now);
        Version version = new Version(versionId, documentId, versionInput.versionLabel(),
                versionInput.documentNumber(), versionInput.issuedAt(), versionInput.publishedAt(),
                versionInput.effectiveFrom(), versionInput.effectiveTo(), versionInput.status(),
                versionInput.sourceUri(), contentHash, versionInput.supersedesVersionId(), now);
        List<Chunk> chunks = chunker.chunk(versionId, normalizedText, now);

        repository.insertDocumentIfAbsent(document);
        repository.insertVersionIfAbsent(version);
        chunks.forEach(repository::insertChunkIfAbsent);
        return new IngestionResult(documentId, versionId, contentHash, chunks.size());
    }

    private static void validate(DocumentInput document, VersionInput version) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(version, "version");
        requireText(document.title(), "title");
        requireText(document.documentType(), "documentType");
        requireText(document.issuer(), "issuer");
        requireText(document.sourceType(), "sourceType");
        requireText(document.jurisdiction(), "jurisdiction");
        requireText(version.versionLabel(), "versionLabel");
        requireText(version.structuredText(), "structuredText");
        Objects.requireNonNull(version.effectiveFrom(), "effectiveFrom");
        Objects.requireNonNull(version.status(), "status");
        if (version.effectiveTo() != null && !version.effectiveTo().isAfter(version.effectiveFrom())) {
            throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
