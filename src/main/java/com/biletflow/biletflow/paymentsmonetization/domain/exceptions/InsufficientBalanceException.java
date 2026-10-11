package com.biletflow.biletflow.paymentsmonetization.domain.exceptions;

public class InsufficientBalanceException extends PaymentsDomainException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
