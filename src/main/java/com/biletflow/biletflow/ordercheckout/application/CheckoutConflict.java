package com.biletflow.biletflow.ordercheckout.application;

public class CheckoutConflict extends IllegalStateException {

    public enum Reason {
        IDEMPOTENCY_KEY_REUSED,
        REGISTRATION_CLOSED,
    }

    private final Reason reason;

    public CheckoutConflict(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
