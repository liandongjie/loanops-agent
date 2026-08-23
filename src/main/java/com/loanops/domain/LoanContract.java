package com.loanops.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanContract(
        Long id,
        String loanNo,
        String borrowerName,
        BigDecimal principal,
        LocalDate startDate,
        LocalDate endDate) {
}
