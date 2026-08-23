package com.loanops.controller;

import com.loanops.dto.LoanStatusResponse;
import com.loanops.service.LoanStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
public class LoanStatusController {

    private final LoanStatusService loanStatusService;

    public LoanStatusController(LoanStatusService loanStatusService) {
        this.loanStatusService = loanStatusService;
    }

    @GetMapping("/{loanNo}/status")
    public LoanStatusResponse getStatus(@PathVariable String loanNo) {
        return loanStatusService.getStatus(loanNo);
    }
}