package com.biletflow.biletflow.paymentsmonetization.domain.exceptions;

public class InvalidPaymentStateException extends PaymentsDomainException {

    public InvalidPaymentStateException(String message) {
        super(message);
    }
}
