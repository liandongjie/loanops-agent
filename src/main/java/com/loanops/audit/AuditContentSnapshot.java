package com.loanops.audit;

public record AuditContentSnapshot(int length, String sha256, String content) {
}