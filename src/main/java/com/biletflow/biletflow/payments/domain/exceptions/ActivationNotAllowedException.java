package com.biletflow.biletflow.payments.domain.exceptions;

public class ActivationNotAllowedException extends PaymentsDomainException {

    public ActivationNotAllowedException(String message) {
        super(message);
    }
}
