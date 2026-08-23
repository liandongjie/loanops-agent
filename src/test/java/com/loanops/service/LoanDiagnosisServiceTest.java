package com.loanops.service;

import com.loanops.domain.LoanContract;
import com.loanops.domain.PaymentRecord;
import com.loanops.domain.RepaymentPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoanDiagnosisServiceTest {

    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 8, 23);
    private LoanDiagnosisService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);
        service = new LoanDiagnosisService(new RepaymentCalculator(), clock);
    }

    @Test
    void caseA_calculatesCurrentRepayment() {
        LoanContract loan = loan(1L, "LN-10001");
        RepaymentPlan plan = plan(11L, 1L, 1, LocalDate.of(2026, 8, 30));

        LoanDiagnosisService.RepaymentDiagnosis result = service.currentRepayment(loan, List.of(plan), List.of());

        assertThat(result.dueAmount()).isEqualByComparingTo("8500.00");
        assertThat(result.paidAmount()).isEqualByComparingTo("0.00");
        assertThat(result.outstandingAmount()).isEqualByComparingTo("8500.00");
        assertThat(result.overdue()).isFalse();
    }

    @Test
    void caseB_calculatesOverdueDiagnosis() {
        LoanContract loan = loan(2L, "LN-10002");
        RepaymentPlan plan = plan(21L, 2L, 1, LocalDate.of(2026, 8, 20));
        PaymentRecord payment = new PaymentRecord(31L, 2L, 21L, LocalDate.of(2026, 8, 19), money("5000.00"));

        LoanDiagnosisService.RepaymentDiagnosis result = service.currentRepayment(loan, List.of(plan), List.of(payment));

        assertThat(result.dueAmount()).isEqualByComparingTo("8500.00");
        assertThat(result.paidAmount()).isEqualByComparingTo("5000.00");
        assertThat(result.outstandingAmount()).isEqualByComparingTo("3500.00");
        assertThat(result.plan().dueDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(result.asOfDate()).isEqualTo(AS_OF_DATE);
        assertThat(result.overdue()).isTrue();
        assertThat(result.overdueDays()).isEqualTo(3);
    }

    @Test
    void caseC_reportsSettledWhenAllPlansArePaid() {
        LoanContract loan = loan(3L, "LN-10003");
        RepaymentPlan first = plan(31L, 3L, 1, LocalDate.of(2026, 8, 20));
        RepaymentPlan second = plan(32L, 3L, 2, LocalDate.of(2026, 9, 20));
        List<PaymentRecord> payments = List.of(
                new PaymentRecord(41L, 3L, 31L, AS_OF_DATE, money("8500.00")),
                new PaymentRecord(42L, 3L, 32L, AS_OF_DATE, money("8500.00")));

        LoanDiagnosisService.SettlementDiagnosis result = service.settlement(loan, List.of(first, second), payments);

        assertThat(result.settled()).isTrue();
        assertThat(result.totalOutstanding()).isEqualByComparingTo("0.00");
        assertThat(service.currentRepayment(loan, List.of(first, second), payments)).isNull();
    }

    @Test
    void dueDateEqualToAsOfDate_isNotOverdue() {
        LoanContract loan = loan(4L, "LN-DUE-TODAY");
        RepaymentPlan plan = plan(41L, 4L, 1, AS_OF_DATE);

        LoanDiagnosisService.RepaymentDiagnosis result = service.currentRepayment(loan, List.of(plan), List.of());

        assertThat(result.overdue()).isFalse();
        assertThat(result.overdueDays()).isZero();
    }

    @Test
    void overpayment_clampsOutstandingToZero() {
        LoanContract loan = loan(5L, "LN-OVERPAID");
        RepaymentPlan plan = plan(51L, 5L, 1, LocalDate.of(2026, 8, 20));
        PaymentRecord payment = new PaymentRecord(52L, 5L, 51L, AS_OF_DATE, money("9000.00"));

        LoanDiagnosisService.SettlementDiagnosis settlement = service.settlement(loan, List.of(plan), List.of(payment));

        assertThat(settlement.settled()).isTrue();
        assertThat(settlement.totalOutstanding()).isEqualByComparingTo("0.00");
        assertThat(service.currentRepayment(loan, List.of(plan), List.of(payment))).isNull();
    }

    @Test
    void currentRepayment_sortsByDueDateThenInstallmentNumber() {
        LoanContract loan = loan(6L, "LN-SORT");
        RepaymentPlan laterDate = plan(61L, 6L, 1, LocalDate.of(2026, 8, 26));
        RepaymentPlan sameDateLaterInstallment = plan(62L, 6L, 2, LocalDate.of(2026, 8, 25));
        RepaymentPlan expected = plan(63L, 6L, 1, LocalDate.of(2026, 8, 25));

        LoanDiagnosisService.RepaymentDiagnosis result = service.currentRepayment(
                loan,
                List.of(laterDate, sameDateLaterInstallment, expected),
                List.of());

        assertThat(result.plan().id()).isEqualTo(expected.id());
        assertThat(result.plan().dueDate()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(result.plan().installmentNo()).isEqualTo(1);
    }

    @Test
    void diagnosis_ignoresPlansAndPaymentsFromOtherLoans() {
        LoanContract loan = loan(7L, "LN-ISOLATED");
        RepaymentPlan ownPlan = plan(71L, 7L, 1, LocalDate.of(2026, 8, 30));
        RepaymentPlan foreignPlan = plan(72L, 8L, 1, LocalDate.of(2026, 8, 10));
        PaymentRecord foreignPaymentWithSamePlanId =
                new PaymentRecord(73L, 8L, 71L, AS_OF_DATE, money("8500.00"));

        LoanDiagnosisService.RepaymentDiagnosis current = service.currentRepayment(
                loan,
                List.of(foreignPlan, ownPlan),
                List.of(foreignPaymentWithSamePlanId));
        LoanDiagnosisService.SettlementDiagnosis settlement = service.settlement(
                loan,
                List.of(foreignPlan, ownPlan),
                List.of(foreignPaymentWithSamePlanId));

        assertThat(current.plan().id()).isEqualTo(ownPlan.id());
        assertThat(current.paidAmount()).isEqualByComparingTo("0.00");
        assertThat(current.outstandingAmount()).isEqualByComparingTo("8500.00");
        assertThat(settlement.settled()).isFalse();
        assertThat(settlement.totalOutstanding()).isEqualByComparingTo("8500.00");
    }

    private static LoanContract loan(Long id, String loanNo) {
        return new LoanContract(id, loanNo, "Test Borrower", money("10000.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
    }

    private static RepaymentPlan plan(Long id, Long loanId, int installmentNo, LocalDate dueDate) {
        return new RepaymentPlan(id, loanId, installmentNo, dueDate, money("8000.00"), money("500.00"));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
