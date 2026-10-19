package com.biletflow.biletflow.paymentsmonetization.application.command;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.OperationResolution;
import java.util.Objects;
import java.util.UUID;

public record ResolvePaymentOperationCommand(
    UUID paymentId,
    long actorId,
    UUID expectedOperationId,
    OperationResolution resolution,
    String bankSupportReference
) {
    public ResolvePaymentOperationCommand {
        Objects.requireNonNull(paymentId, "paymentId cannot be null");
        Objects.requireNonNull(resolution, "resolution cannot be null");
        if (
            bankSupportReference == null || bankSupportReference.isBlank() || bankSupportReference.length() > 200
        ) throw new IllegalArgumentException("Bank support reference is required (max 200 characters)");
    }
}
