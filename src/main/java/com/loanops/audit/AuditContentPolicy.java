package com.loanops.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class AuditContentPolicy {

    private final boolean includeContent;

    public AuditContentPolicy(@Value("${loanops.audit.include-content:false}") boolean includeContent) {
        this.includeContent = includeContent;
    }

    public AuditContentSnapshot snapshot(String value) {
        if (value == null) {
            return new AuditContentSnapshot(0, null, null);
        }
        String hash = sha256(value);
        return new AuditContentSnapshot(value.length(), hash, includeContent ? value : null);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}