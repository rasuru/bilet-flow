package com.biletflow.biletflow.promotions.ohs;

import com.biletflow.biletflow.common.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PromotionsOhs {
    record Line(UUID ticketTypeId, Money unitPrice, int quantity) {}

    record Quote(UUID campaignId, String code, Money eligibleSubtotal, Money discountAmount) {}

    record Reservation(UUID redemptionId, UUID campaignId, String code, Money discountAmount, Instant expiresAt) {}

    Quote quote(UUID eventId, UUID checkoutId, String code, List<Line> lines);

    Reservation reserve(UUID eventId, UUID checkoutId, String code, List<Line> lines, Instant expiresAt);

    void confirm(UUID redemptionId, UUID orderId);

    void release(UUID redemptionId, UUID checkoutId);
}
