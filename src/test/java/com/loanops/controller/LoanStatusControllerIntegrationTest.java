package com.loanops.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(LoanStatusControllerIntegrationTest.FixedClockConfiguration.class)
class LoanStatusControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ln10001_returnsCurrentRepaymentStatus() throws Exception {
        mockMvc.perform(get("/api/loans/LN-10001/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanNo").value("LN-10001"))
                .andExpect(jsonPath("$.dueAmount").value(8500.00))
                .andExpect(jsonPath("$.paidAmount").value(0.00))
                .andExpect(jsonPath("$.outstandingAmount").value(8500.00))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.overdueDays").value(0))
                .andExpect(jsonPath("$.settled").value(false));
    }

    @Test
    void ln10002_returnsDeterministicOverdueStatus() throws Exception {
        mockMvc.perform(get("/api/loans/LN-10002/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanNo").value("LN-10002"))
                .andExpect(jsonPath("$.installmentNo").value(1))
                .andExpect(jsonPath("$.dueDate").value("2026-08-20"))
                .andExpect(jsonPath("$.asOfDate").value("2026-08-23"))
                .andExpect(jsonPath("$.dueAmount").value(8500.00))
                .andExpect(jsonPath("$.paidAmount").value(5000.00))
                .andExpect(jsonPath("$.outstandingAmount").value(3500.00))
                .andExpect(jsonPath("$.overdue").value(true))
                .andExpect(jsonPath("$.overdueDays").value(3))
                .andExpect(jsonPath("$.settled").value(false))
                .andExpect(jsonPath("$.totalOutstanding").value(3500.00));
    }

    @Test
    void ln10003_returnsSettledStatus() throws Exception {
        mockMvc.perform(get("/api/loans/LN-10003/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanNo").value("LN-10003"))
                .andExpect(jsonPath("$.asOfDate").value("2026-08-23"))
                .andExpect(jsonPath("$.settled").value(true))
                .andExpect(jsonPath("$.totalOutstanding").value(0.00))
                .andExpect(jsonPath("$.installmentNo").doesNotExist());
    }

    @Test
    void unknownLoan_returns404() throws Exception {
        mockMvc.perform(get("/api/loans/LN-NOT-FOUND/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"));
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
