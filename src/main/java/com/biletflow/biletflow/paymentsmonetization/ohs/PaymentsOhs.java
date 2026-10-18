package com.biletflow.biletflow.paymentsmonetization.ohs;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published PM contract; callers never use PM repositories or provider-specific types. */
public interface PaymentsOhs {
    record Start(UUID orderId, UUID eventId, long payerId, BigDecimal amount, Instant expiresAt) {}

    record PaymentView(
        UUID id,
        UUID orderId,
        String status,
        UUID refundId,
        String refundStatus,
        boolean reviewRequired,
        String bankOperation,
        UUID bankOperationId,
        String bankOperationState,
        boolean sandbox
    ) {}

    PaymentView initiate(Start command);
    PaymentView get(UUID paymentId);
    void abandon(UUID paymentId);
    UUID refund(UUID paymentId, String reason);
}
