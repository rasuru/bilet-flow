package com.biletflow.biletflow.paymentsmonetization.persistence.notice.entity;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentNoticeStore;
import com.biletflow.biletflow.paymentsmonetization.domain.notice.PaymentNoticeKind;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pm_payment_notice")
public class PaymentNoticeJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "kind", nullable = false, length = 40, updatable = false)
    @Enumerated(EnumType.STRING)
    private PaymentNoticeKind kind;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private long recipientId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "amount_kzt", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amountKzt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "token")
    private UUID token;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PaymentNoticeJpaEntity() {}

    public PaymentNoticeJpaEntity(PaymentNoticeStore.PaymentNotice notice) {
        this.id = notice.id();
        this.kind = notice.kind();
        this.recipientId = notice.recipientId();
        this.eventId = notice.eventId();
        this.paymentId = notice.paymentId();
        this.amountKzt = notice.amountKzt();
        this.createdAt = notice.createdAt();
        this.nextAttemptAt = notice.createdAt();
    }

    public PaymentNoticeStore.Claim claim(Instant now) {
        token = UUID.randomUUID();
        leaseUntil = now.plusSeconds(120);
        return new PaymentNoticeStore.Claim(
            token,
            new PaymentNoticeStore.PaymentNotice(id, kind, recipientId, eventId, paymentId, amountKzt, createdAt)
        );
    }

    /** Ignores a stale claim whose lease was taken over by another worker. */
    public void complete(PaymentNoticeStore.Claim claim, Instant now) {
        if (sentAt == null && claim.token().equals(token)) {
            sentAt = now;
            token = null;
            leaseUntil = null;
        }
    }

    /** Backs off from 30 seconds, doubling up to one hour. Notices are never discarded. */
    public void retry(PaymentNoticeStore.Claim claim, Instant now) {
        if (sentAt == null && claim.token().equals(token)) {
            attempts++;
            nextAttemptAt = now.plusSeconds(Math.min(3600, 30L << Math.min(attempts - 1, 7)));
            token = null;
            leaseUntil = null;
        }
    }
}
