package com.biletflow.biletflow.paymentsmonetization.ohs;

import com.biletflow.biletflow.common.events.IntegrationEvent;
import java.util.Map;
import java.util.UUID;

/** Facts published by PM, consumed by Order & Checkout. Stable event ID makes publication idempotent. */
public record PaymentOutcome(UUID paymentId, UUID orderId, String outcome, UUID refundId) {
    public static final String TYPE = "payments.ticket-outcome.v1";

    public IntegrationEvent event() {
        String key = paymentId + ":" + outcome + ":" + refundId;
        return new IntegrationEvent(
            UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            TYPE,
            paymentId,
            Map.of("orderId", orderId.toString(), "outcome", outcome, "refundId", refundId == null ? "" : refundId.toString())
        );
    }

    public static PaymentOutcome from(IntegrationEvent event) {
        String refund = event.attributes().get("refundId");
        return new PaymentOutcome(
            event.aggregateId(),
            UUID.fromString(event.attributes().get("orderId")),
            event.attributes().get("outcome"),
            refund == null || refund.isEmpty() ? null : UUID.fromString(refund)
        );
    }
}
