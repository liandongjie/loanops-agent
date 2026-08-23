package com.loanops.service;

import com.loanops.domain.LoanContract;
import com.loanops.domain.PaymentRecord;
import com.loanops.domain.RepaymentPlan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class LoanDiagnosisService {

    private final RepaymentCalculator repaymentCalculator;
    private final Clock clock;

    public LoanDiagnosisService(RepaymentCalculator repaymentCalculator, Clock clock) {
        this.repaymentCalculator = repaymentCalculator;
        this.clock = clock;
    }

    public RepaymentDiagnosis currentRepayment(
            LoanContract loan, List<RepaymentPlan> plans, List<PaymentRecord> payments) {
        List<RepaymentPlan> loanPlans = plansForLoan(loan, plans);
        List<PaymentRecord> loanPayments = paymentsForLoan(loan, payments);

        return loanPlans.stream()
                .map(plan -> repaymentCalculator.calculate(plan, loanPayments))
                .filter(result -> result.outstandingAmount().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing((RepaymentCalculator.RepaymentResult result) -> result.plan().dueDate())
                        .thenComparing(result -> result.plan().installmentNo()))
                .findFirst()
                .map(this::toDiagnosis)
                .orElse(null);
    }

    public SettlementDiagnosis settlement(
            LoanContract loan, List<RepaymentPlan> plans, List<PaymentRecord> payments) {
        List<RepaymentPlan> loanPlans = plansForLoan(loan, plans);
        List<PaymentRecord> loanPayments = paymentsForLoan(loan, payments);

        BigDecimal totalOutstanding = loanPlans.stream()
                .map(plan -> repaymentCalculator.calculate(plan, loanPayments).outstandingAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SettlementDiagnosis(totalOutstanding.compareTo(BigDecimal.ZERO) == 0, totalOutstanding);
    }

    private List<RepaymentPlan> plansForLoan(LoanContract loan, List<RepaymentPlan> plans) {
        // 领域服务负责守住贷款聚合边界，避免调用方混入其他贷款的计划后产生误诊断。
        return plans.stream()
                .filter(plan -> Objects.equals(plan.loanId(), loan.id()))
                .toList();
    }

    private List<PaymentRecord> paymentsForLoan(LoanContract loan, List<PaymentRecord> payments) {
        return payments.stream()
                .filter(payment -> Objects.equals(payment.loanId(), loan.id()))
                .toList();
    }

    private RepaymentDiagnosis toDiagnosis(RepaymentCalculator.RepaymentResult result) {
        LocalDate asOfDate = LocalDate.now(clock);
        LocalDate dueDate = result.plan().dueDate();
        boolean overdue = result.outstandingAmount().compareTo(BigDecimal.ZERO) > 0 && asOfDate.isAfter(dueDate);
        long overdueDays = overdue ? ChronoUnit.DAYS.between(dueDate, asOfDate) : 0;
        return new RepaymentDiagnosis(
                result.plan(), result.dueAmount(), result.paidAmount(), result.outstandingAmount(),
                asOfDate, overdue, overdueDays);
    }

    public record RepaymentDiagnosis(
            RepaymentPlan plan,
            BigDecimal dueAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            LocalDate asOfDate,
            boolean overdue,
            long overdueDays) {
    }

    public record SettlementDiagnosis(boolean settled, BigDecimal totalOutstanding) {
    }
}
