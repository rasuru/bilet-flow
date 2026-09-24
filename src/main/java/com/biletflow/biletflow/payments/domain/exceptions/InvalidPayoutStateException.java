package com.biletflow.biletflow.payments.domain.exceptions;

public class InvalidPayoutStateException extends PaymentsDomainException {
    public InvalidPayoutStateException(String message) {
        super(message);
    }
}
