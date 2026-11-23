package com.biletflow.biletflow.promotions.application.redemption.command;

import java.time.Instant;
import java.util.UUID;

public record ConfirmRedemptionCommand(UUID redemptionId, UUID orderId, Instant acceptedAt) {
    public ConfirmRedemptionCommand(UUID redemptionId, UUID orderId) {
        this(redemptionId, orderId, null);
    }
}
