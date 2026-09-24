package com.biletflow.biletflow.payments.domain.exceptions;

public class RefundExceedsPaymentException extends PaymentsDomainException {

    public RefundExceedsPaymentException(String message) {
        super(message);
    }
}
