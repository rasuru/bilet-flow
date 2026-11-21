package com.biletflow.biletflow.promotions.application.redemption.command;

import com.biletflow.biletflow.promotions.domain.redemption.RedemptionSource;
import java.util.List;
import java.util.UUID;

public record ReservePromotionCommand(UUID eventId, UUID checkoutId, String code, List<PromotionLine> lines, RedemptionSource source) {
    public ReservePromotionCommand {
        lines = lines == null ? List.of() : List.copyOf(lines);
        source = source == null ? RedemptionSource.CODE : source;
    }
}
