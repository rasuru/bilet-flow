package com.biletflow.biletflow.promotions.application.campaign.command;

import com.biletflow.biletflow.promotions.domain.campaign.DiscountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreateCampaignCommand(
    UUID eventId,
    String name,
    DiscountType discountType,
    BigDecimal discountValue,
    Instant validFrom,
    Instant validUntil,
    Set<UUID> applicableTicketTypeIds,
    Integer maxRedemptions
) {
    public CreateCampaignCommand {
        applicableTicketTypeIds = applicableTicketTypeIds == null ? Set.of() : Set.copyOf(applicableTicketTypeIds);
    }
}
