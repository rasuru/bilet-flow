package com.biletflow.biletflow.ordercheckout.application.refundpolicy;

import java.time.Instant;
import java.util.UUID;

public record RefundPolicyView(UUID eventId, boolean refundsAllowed, int cutoffHoursBeforeEvent, String terms, Instant updatedAt) {
    public RefundPolicyView(UUID eventId, boolean refundsAllowed, int cutoffHoursBeforeEvent, Instant updatedAt) {
        this(eventId, refundsAllowed, cutoffHoursBeforeEvent, null, updatedAt);
    }
}
