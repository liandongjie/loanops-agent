package com.loanops.policy;

public record PolicyRuntimePreparation(
        PolicyGroundingContext context,
        PolicyRetrievalAuditHandle auditHandle) {}
