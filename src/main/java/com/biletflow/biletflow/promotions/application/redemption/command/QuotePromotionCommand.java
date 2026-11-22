package com.biletflow.biletflow.promotions.application.redemption.command;

import java.util.List;
import java.util.UUID;

public record QuotePromotionCommand(UUID eventId, UUID checkoutId, String code, List<PromotionLine> lines) {
    public QuotePromotionCommand {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
