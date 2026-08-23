package com.loanops.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RepaymentPlan(
        Long id,
        Long loanId,
        int installmentNo,
        LocalDate dueDate,
        BigDecimal principalDue,
        BigDecimal interestDue) {
}
