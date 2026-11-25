package com.biletflow.biletflow.eventmanagement.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** A null lead time means all requests go through support. Zero means up to the event start. */
public record RefundPolicy(Integer hoursBeforeStart) {
    public static final int MAX_HOURS = 8760;

    public RefundPolicy {
        if (hoursBeforeStart != null && (hoursBeforeStart < 0 || hoursBeforeStart > MAX_HOURS)) {
            throw new IllegalArgumentException("Refund lead time must be between 0 and 8760 hours");
        }
    }

    public Instant deadline(Instant startsAt) {
        return hoursBeforeStart == null ? null : startsAt.minus(hoursBeforeStart, ChronoUnit.HOURS);
    }
}
