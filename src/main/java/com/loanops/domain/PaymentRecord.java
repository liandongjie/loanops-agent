package com.loanops.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRecord(
        Long id,
        Long loanId,
        Long repaymentPlanId,
        LocalDate paymentDate,
        BigDecimal amount) {
}
