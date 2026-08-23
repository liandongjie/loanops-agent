package com.loanops.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CurrentRepaymentFacts(
        String loanNo,
        boolean currentRepaymentAvailable,
        boolean settled,
        Integer installmentNo,
        LocalDate dueDate,
        BigDecimal principalDue,
        BigDecimal interestDue,
        BigDecimal dueAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        boolean overdue,
        long overdueDays) {
}