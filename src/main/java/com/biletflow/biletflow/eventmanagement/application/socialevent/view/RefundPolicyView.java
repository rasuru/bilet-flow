package com.biletflow.biletflow.eventmanagement.application.socialevent.view;

import com.biletflow.biletflow.eventmanagement.domain.EventDateRange;
import com.biletflow.biletflow.eventmanagement.domain.RefundPolicy;
import java.time.Instant;

public record RefundPolicyView(boolean refundsAllowed, int cutoffHoursBeforeStart, Instant refundableUntil, String summary, String terms) {
    public static RefundPolicyView from(RefundPolicy policy, EventDateRange dateRange) {
        return new RefundPolicyView(
            policy.refundsAllowed(),
            policy.cutoffHoursBeforeStart(),
            policy.refundableUntil(dateRange).orElse(null),
            policy.summary(),
            policy.terms()
        );
    }
}
