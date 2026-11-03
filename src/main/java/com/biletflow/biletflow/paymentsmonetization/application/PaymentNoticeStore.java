package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.paymentsmonetization.domain.notice.PaymentNoticeKind;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public interface PaymentNoticeStore {
    record PaymentNotice(
        UUID id,
        PaymentNoticeKind kind,
        long recipientId,
        UUID eventId,
        UUID paymentId,
        BigDecimal amountKzt,
        Instant createdAt
    ) {
        public PaymentNotice {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(kind, "kind cannot be null");
            Objects.requireNonNull(eventId, "eventId cannot be null");
            Objects.requireNonNull(paymentId, "paymentId cannot be null");
            Objects.requireNonNull(amountKzt, "amountKzt cannot be null");
            Objects.requireNonNull(createdAt, "createdAt cannot be null");
            if (recipientId <= 0) throw new IllegalArgumentException("Invalid notice recipient");
        }

        /** One notice per payment and kind, so a repeated bank status cannot send a second email. */
        public static UUID idFor(UUID paymentId, PaymentNoticeKind kind) {
            return UUID.nameUUIDFromBytes((paymentId + ":" + kind.name()).getBytes(StandardCharsets.UTF_8));
        }
    }

    record Claim(UUID token, PaymentNotice notice) {}

    /** Requires an active transaction. Enqueuing an existing notice ID has no effect. */
    void enqueue(PaymentNotice notice);

    Optional<Claim> claim(Instant now);

    void complete(Claim claim, Instant now);

    void retry(Claim claim, Instant now);
}
