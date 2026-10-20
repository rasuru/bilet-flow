package com.biletflow.biletflow.paymentsmonetization.application;

/** Sanitized provider failure; never includes response bodies, credentials, tokens or raw exception messages. */
public class PaymentProviderUnavailableException extends RuntimeException {

    public enum Kind {
        HTTP,
        TIMEOUT,
        CONNECTION,
        INTERRUPTED,
        INVALID_RESPONSE,
    }

    public enum Phase {
        TOKEN,
        STATUS,
        MUTATION,
        UNKNOWN,
    }

    private final Phase phase;
    private final Kind kind;
    private final Integer httpStatus;

    public PaymentProviderUnavailableException() {
        this(Kind.INVALID_RESPONSE, null, Phase.UNKNOWN);
    }

    public PaymentProviderUnavailableException(Kind kind, Integer httpStatus, Phase phase) {
        super("Payment provider is unavailable; payment will be reconciled");
        this.kind = java.util.Objects.requireNonNull(kind);
        this.phase = java.util.Objects.requireNonNull(phase);
        this.httpStatus = httpStatus;
    }

    public boolean isHttpStatus(int status) {
        return kind == Kind.HTTP && httpStatus != null && httpStatus == status;
    }

    public String diagnostic() {
        return phase.name() + " " + (httpStatus == null ? kind.name() : "HTTP " + httpStatus);
    }
}
