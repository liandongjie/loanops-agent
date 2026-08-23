package com.loanops.tool;

import com.loanops.dto.CurrentRepaymentFacts;
import com.loanops.dto.OverdueDiagnosisFacts;
import com.loanops.dto.SettlementStatusFacts;
import com.loanops.exception.LoanNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(LoanOpsToolsIntegrationTest.FixedClockConfiguration.class)
class LoanOpsToolsIntegrationTest {

    @Autowired
    private LoanOpsTools tools;

    @Test
    void getCurrentRepayment_returnsCaseAJavaFacts() {
        CurrentRepaymentFacts result = tools.getCurrentRepayment("LN-10001");

        assertThat(result.loanNo()).isEqualTo("LN-10001");
        assertThat(result.currentRepaymentAvailable()).isTrue();
        assertThat(result.settled()).isFalse();
        assertThat(result.installmentNo()).isEqualTo(1);
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(result.principalDue()).isEqualByComparingTo("8000.00");
        assertThat(result.interestDue()).isEqualByComparingTo("500.00");
        assertThat(result.dueAmount()).isEqualByComparingTo("8500.00");
        assertThat(result.paidAmount()).isEqualByComparingTo("0.00");
        assertThat(result.outstandingAmount()).isEqualByComparingTo("8500.00");
        assertThat(result.overdue()).isFalse();
        assertThat(result.overdueDays()).isZero();
    }

    @Test
    void getOverdueDiagnosis_returnsCaseBJavaFacts() {
        OverdueDiagnosisFacts result = tools.getOverdueDiagnosis("LN-10002");

        assertThat(result.loanNo()).isEqualTo("LN-10002");
        assertThat(result.currentRepaymentAvailable()).isTrue();
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(result.asOfDate()).isEqualTo(LocalDate.of(2026, 8, 23));
        assertThat(result.dueAmount()).isEqualByComparingTo("8500.00");
        assertThat(result.paidAmount()).isEqualByComparingTo("5000.00");
        assertThat(result.outstandingAmount()).isEqualByComparingTo("3500.00");
        assertThat(result.overdue()).isTrue();
        assertThat(result.overdueDays()).isEqualTo(3);
    }

    @Test
    void getSettlementStatus_returnsCaseCJavaFacts() {
        SettlementStatusFacts result = tools.getSettlementStatus("LN-10003");

        assertThat(result.loanNo()).isEqualTo("LN-10003");
        assertThat(result.settled()).isTrue();
        assertThat(result.totalOutstanding()).isEqualByComparingTo("0.00");
    }

    @Test
    void unknownLoan_neverReturnsInventedFacts() {
        assertThatThrownBy(() -> tools.getOverdueDiagnosis("LN-NOT-FOUND"))
                .isInstanceOf(LoanNotFoundException.class)
                .hasMessageContaining("LN-NOT-FOUND");
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}