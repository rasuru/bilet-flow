package com.biletflow.biletflow.promotions.rest;

import com.biletflow.biletflow.promotions.domain.campaign.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreateCampaignRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull DiscountType discountType,
    @NotNull @Positive BigDecimal discountValue,
    @NotNull Instant validFrom,
    @NotNull Instant validUntil,
    Set<UUID> applicableTicketTypeIds,
    @Positive Integer maxRedemptions
) {}
