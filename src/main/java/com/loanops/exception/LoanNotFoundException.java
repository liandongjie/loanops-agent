package com.loanops.exception;

public class LoanNotFoundException extends RuntimeException {

    public LoanNotFoundException(String loanNo) {
        super("Loan not found: " + loanNo);
    }
}