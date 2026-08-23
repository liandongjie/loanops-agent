package com.loanops.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanStatusResponse(
        String loanNo,
        Integer installmentNo,
        LocalDate dueDate,
        LocalDate asOfDate,
        BigDecimal dueAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        boolean overdue,
        long overdueDays,
        boolean settled,
        BigDecimal totalOutstanding) {
}