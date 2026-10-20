package com.biletflow.biletflow.ordercheckout.application.refund;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable receipt snapshot. The purchaser owns the financial receipt, independently of ticket ownership. */
public record RefundNotice(UUID id, UUID orderId, UUID eventId, long purchaserId, String title, BigDecimal amountKzt, Instant confirmedAt) {
    public RefundNotice {
        Objects.requireNonNull(id);
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(title);
        Objects.requireNonNull(amountKzt);
        Objects.requireNonNull(confirmedAt);
        if (amountKzt.signum() <= 0) throw new IllegalArgumentException("A refund receipt requires a positive amount");
    }
}
