package com.loanops.exception;

public record ApiError(int status, String code, String message) {
}