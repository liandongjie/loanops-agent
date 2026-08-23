package com.loanops.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OverdueDiagnosisFacts(
        String loanNo,
        boolean currentRepaymentAvailable,
        boolean settled,
        LocalDate dueDate,
        LocalDate asOfDate,
        BigDecimal dueAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        boolean overdue,
        long overdueDays) {
}