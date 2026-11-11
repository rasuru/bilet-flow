package com.biletflow.biletflow.promotions.application.redemption.command;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservePromotionCommand(UUID eventId, UUID checkoutId, String code, List<PromotionLine> lines, Instant expiresAt) {
    public ReservePromotionCommand {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
