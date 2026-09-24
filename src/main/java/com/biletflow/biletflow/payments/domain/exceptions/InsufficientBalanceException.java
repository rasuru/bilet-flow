package com.biletflow.biletflow.payments.domain.exceptions;

public class InsufficientBalanceException extends PaymentsDomainException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
