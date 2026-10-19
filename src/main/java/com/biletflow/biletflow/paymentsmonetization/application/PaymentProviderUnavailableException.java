package com.biletflow.biletflow.paymentsmonetization.application;

/** Sanitized provider failure; never includes responses, credentials or payment form tokens. */
public class PaymentProviderUnavailableException extends RuntimeException {

    public PaymentProviderUnavailableException() {
        super("Payment provider is unavailable; payment will be reconciled");
    }
}
