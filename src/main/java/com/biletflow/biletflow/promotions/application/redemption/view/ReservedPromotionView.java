package com.biletflow.biletflow.promotions.application.redemption.view;

import com.biletflow.biletflow.common.domain.Money;
import java.time.Instant;
import java.util.UUID;

public record ReservedPromotionView(UUID redemptionId, UUID campaignId, String code, Money discountAmount, Instant expiresAt) {}
