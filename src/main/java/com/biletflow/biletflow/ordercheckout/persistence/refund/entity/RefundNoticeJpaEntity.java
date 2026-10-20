package com.biletflow.biletflow.ordercheckout.persistence.refund.entity;

import com.biletflow.biletflow.ordercheckout.application.refund.RefundNotice;
import com.biletflow.biletflow.ordercheckout.application.refund.RefundNoticeStore.Claim;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_checkout_refund_notice")
public class RefundNoticeJpaEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "purchaser_id", nullable = false, updatable = false)
    private long purchaserId;

    @Column(nullable = false, updatable = false, length = 255)
    private String title;

    @Column(name = "amount_kzt", nullable = false, updatable = false, precision = 21, scale = 2)
    private BigDecimal amountKzt;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Version
    private long version;

    protected RefundNoticeJpaEntity() {}

    public RefundNoticeJpaEntity(RefundNotice notice) {
        id = notice.id();
        orderId = notice.orderId();
        eventId = notice.eventId();
        purchaserId = notice.purchaserId();
        title = notice.title();
        amountKzt = notice.amountKzt();
        confirmedAt = notice.confirmedAt();
        nextAttemptAt = notice.confirmedAt();
    }

    public Claim claim(Instant now) {
        if (sentAt != null || nextAttemptAt.isAfter(now) || (leaseUntil != null && leaseUntil.isAfter(now))) {
            throw new IllegalStateException("Refund notice is not available");
        }
        claimToken = UUID.randomUUID();
        leaseUntil = now.plusSeconds(120);
        attempts = Math.min(attempts + 1, 1000000);
        return new Claim(new RefundNotice(id, orderId, eventId, purchaserId, title, amountKzt, confirmedAt), claimToken);
    }

    public void complete(Claim claim, Instant now) {
        if (!owns(claim)) return;
        sentAt = now;
        claimToken = null;
        leaseUntil = null;
    }

    public void retry(Claim claim, Instant now) {
        if (!owns(claim)) return;
        nextAttemptAt = now.plusSeconds(Math.min(3600, 30L << Math.min(Math.max(0, attempts - 1), 7)));
        claimToken = null;
        leaseUntil = null;
    }

    private boolean owns(Claim claim) {
        return sentAt == null && claim.token().equals(claimToken);
    }
}
