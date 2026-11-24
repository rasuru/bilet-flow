package com.biletflow.biletflow.ordercheckout.application.refundpolicy;

import java.time.Instant;
import java.util.UUID;

public interface AttendeeRefundPolicy {
    boolean allowsAttendeeRefund(UUID eventId, Instant requestedAt);

    AttendeeRefundPolicy DISABLED = (eventId, requestedAt) -> false;
}
