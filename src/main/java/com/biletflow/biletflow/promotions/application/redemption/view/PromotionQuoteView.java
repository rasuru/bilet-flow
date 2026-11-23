package com.biletflow.biletflow.promotions.application.redemption.view;

import com.biletflow.biletflow.common.domain.Money;
import java.util.UUID;

public record PromotionQuoteView(UUID campaignId, String code, Money eligibleSubtotal, Money discountAmount) {}
