package com.biletflow.biletflow.payments.domain.exceptions;

public class InvalidPaymentStateException extends PaymentsDomainException {

    public InvalidPaymentStateException(String message) {
        super(message);
    }
}
