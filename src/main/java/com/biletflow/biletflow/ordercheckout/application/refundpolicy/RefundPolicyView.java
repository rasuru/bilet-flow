package com.biletflow.biletflow.ordercheckout.application.refundpolicy;

import java.time.Instant;
import java.util.UUID;

public record RefundPolicyView(UUID eventId, boolean refundsAllowed, int cutoffHoursBeforeEvent, Instant updatedAt) {}
