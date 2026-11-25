package com.biletflow.biletflow.eventmanagement.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public record RefundPolicy(boolean refundsAllowed, int cutoffHoursBeforeStart, String terms) {
    public static final int MAX_CUTOFF_HOURS = 24 * 365;
    public static final int MAX_TERMS_LENGTH = 2000;

    public RefundPolicy {
        if (cutoffHoursBeforeStart < 0 || cutoffHoursBeforeStart > MAX_CUTOFF_HOURS) {
            throw new IllegalArgumentException("Refund cutoff must be between 0 and " + MAX_CUTOFF_HOURS + " hours before the event");
        }
        if (!refundsAllowed && cutoffHoursBeforeStart != 0) {
            throw new IllegalArgumentException("A refund cutoff only applies when refunds are allowed");
        }
        terms = terms == null || terms.isBlank() ? null : terms.strip();
        if (terms != null && terms.length() > MAX_TERMS_LENGTH) {
            throw new IllegalArgumentException("Refund terms cannot exceed " + MAX_TERMS_LENGTH + " characters");
        }
    }

    public Optional<Instant> refundableUntil(EventDateRange dateRange) {
        if (!refundsAllowed) return Optional.empty();
        return Optional.of(dateRange.startAt().minus(Duration.ofHours(cutoffHoursBeforeStart)));
    }

    public String summary() {
        if (!refundsAllowed) return "No refunds";
        if (cutoffHoursBeforeStart == 0) return "Refunds until the event starts";
        return "Refunds until " + cutoffHoursBeforeStart + " hours before the event";
    }
}
