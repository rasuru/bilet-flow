package com.biletflow.biletflow.paymentsmonetization.domain.settlement;

public enum PaymentProvider {
    HALYK,
    MOCK;

    /** The immutable invoice namespace routes old and new work independently of the current setting. */
    public static PaymentProvider forInvoice(String invoice) {
        return invoice.startsWith("M") ? MOCK : HALYK;
    }
}
