package com.loanops.service;

import com.loanops.domain.PaymentRecord;
import com.loanops.domain.RepaymentPlan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RepaymentCalculator {

    public RepaymentResult calculate(RepaymentPlan plan, List<PaymentRecord> payments) {
        BigDecimal dueAmount = plan.principalDue().add(plan.interestDue());
        BigDecimal paidAmount = payments.stream()
                .filter(payment -> payment.repaymentPlanId().equals(plan.id()))
                .map(PaymentRecord::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstandingAmount = dueAmount.subtract(paidAmount).max(BigDecimal.ZERO);
        return new RepaymentResult(plan, dueAmount, paidAmount, outstandingAmount);
    }

    public record RepaymentResult(
            RepaymentPlan plan,
            BigDecimal dueAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount) {
    }
}
