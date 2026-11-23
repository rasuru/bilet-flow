package com.biletflow.biletflow.promotions.ohs;

import com.biletflow.biletflow.common.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PromotionsOhs {
    enum Source {
        CODE,
        CAMPAIGN_LINK,
    }

    record Line(UUID ticketTypeId, Money unitPrice, int quantity) {}

    record Quote(UUID campaignId, String code, Money eligibleSubtotal, Money discountAmount) {}

    record Reservation(UUID redemptionId, UUID campaignId, String code, Money discountAmount, Instant expiresAt) {}

    Quote quote(UUID eventId, UUID checkoutId, String code, List<Line> lines);

    default Reservation reserve(UUID eventId, UUID checkoutId, String code, List<Line> lines, Source source) {
        return reserve(eventId, checkoutId, code, lines, source, null);
    }

    Reservation reserve(UUID eventId, UUID checkoutId, String code, List<Line> lines, Source source, Instant checkoutDeadline);

    default void confirm(UUID redemptionId, UUID orderId) {
        confirm(redemptionId, orderId, null);
    }

    void confirm(UUID redemptionId, UUID orderId, Instant acceptedAt);

    void release(UUID redemptionId, UUID checkoutId);
}
