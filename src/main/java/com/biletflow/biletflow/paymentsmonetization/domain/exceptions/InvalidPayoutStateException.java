package com.biletflow.biletflow.paymentsmonetization.domain.exceptions;

public class InvalidPayoutStateException extends PaymentsDomainException {
    public InvalidPayoutStateException(String message) {
        super(message);
    }
}
