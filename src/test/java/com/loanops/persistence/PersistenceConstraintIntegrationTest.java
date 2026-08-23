package com.loanops.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PersistenceConstraintIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void validPaymentPlanRelationship_isAccepted() {
        int updated = jdbcTemplate.update("""
                INSERT INTO payment_record (id, loan_id, repayment_plan_id, payment_date, amount)
                VALUES (?, ?, ?, ?, ?)
                """, 901L, 2L, 21L, java.sql.Date.valueOf("2026-08-19"), new java.math.BigDecimal("1.00"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_record WHERE id = 901", Integer.class);

        assertThat(updated).isEqualTo(1);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void crossLoanPaymentPlanRelationship_isRejected() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO payment_record (id, loan_id, repayment_plan_id, payment_date, amount)
                VALUES (?, ?, ?, ?, ?)
                """, 902L, 3L, 21L, java.sql.Date.valueOf("2026-08-19"), new java.math.BigDecimal("1.00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentInstallmentsForSameLoan_areAllowed() {
        int updated = jdbcTemplate.update("""
                INSERT INTO repayment_plan (id, loan_id, installment_no, due_date, principal_due, interest_due)
                VALUES (?, ?, ?, ?, ?, ?)
                """, 12L, 1L, 2, java.sql.Date.valueOf("2026-09-30"),
                new java.math.BigDecimal("1.00"), new java.math.BigDecimal("0.00"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM repayment_plan WHERE loan_id = 1 AND installment_no = 2", Integer.class);

        assertThat(updated).isEqualTo(1);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void duplicateInstallmentForSameLoan_isRejected() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO repayment_plan (id, loan_id, installment_no, due_date, principal_due, interest_due)
                VALUES (?, ?, ?, ?, ?, ?)
                """, 13L, 1L, 1, java.sql.Date.valueOf("2026-09-30"),
                new java.math.BigDecimal("1.00"), new java.math.BigDecimal("0.00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
