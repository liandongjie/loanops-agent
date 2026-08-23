package com.loanops.dto;

import java.math.BigDecimal;

public record SettlementStatusFacts(
        String loanNo,
        boolean settled,
        BigDecimal totalOutstanding) {
}