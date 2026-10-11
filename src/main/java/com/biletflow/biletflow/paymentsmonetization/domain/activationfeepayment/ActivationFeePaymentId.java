package com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment;

import java.util.Objects;
import java.util.UUID;

public record ActivationFeePaymentId(UUID value) {
    public ActivationFeePaymentId {
        Objects.requireNonNull(value, "ActivationFeePaymentId cannot be null");
    }

    public static ActivationFeePaymentId generate() {
        return new ActivationFeePaymentId(UUID.randomUUID());
    }
}
