package com.biletflow.biletflow.paymentsmonetization.persistence.payout.entity;

import com.biletflow.biletflow.paymentsmonetization.application.PayoutNoticeStore;
import com.biletflow.biletflow.paymentsmonetization.application.view.PayoutView;
import com.biletflow.biletflow.paymentsmonetization.domain.payout.PayoutStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pm_payout")
public class PayoutJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organizer_id", nullable = false, updatable = false)
    private long organizerId;

    @Column(name = "amount_kzt", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amountKzt;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "notice_next_attempt_at")
    private Instant noticeNextAttemptAt;

    @Column(name = "notice_attempts", nullable = false)
    private int noticeAttempts;

    @Column(name = "notice_token")
    private UUID noticeToken;

    @Column(name = "notice_lease_until")
    private Instant noticeLeaseUntil;

    @Column(name = "notice_sent_at")
    private Instant noticeSentAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PayoutJpaEntity() {}

    public PayoutJpaEntity(UUID id, long organizerId, BigDecimal amountKzt, Instant requestedAt) {
        this.id = id;
        this.organizerId = organizerId;
        this.amountKzt = amountKzt;
        this.requestedAt = requestedAt;
        this.status = PayoutStatus.REQUESTED;
    }

    public void updateMutableState(PayoutStatus status, String reference, String failure, Instant completedAt) {
        this.status = status;
        this.providerReference = reference;
        this.failureReason = failure;
        this.completedAt = completedAt;
        if (completedAt != null && noticeNextAttemptAt == null) noticeNextAttemptAt = completedAt;
    }

    public PayoutNoticeStore.Claim claimNotice(Instant now) {
        noticeToken = UUID.randomUUID();
        noticeLeaseUntil = now.plusSeconds(120);
        return new PayoutNoticeStore.Claim(
            noticeToken,
            organizerId,
            new PayoutView(id, amountKzt, "KZT", status.name(), requestedAt, completedAt, providerReference, failureReason, true)
        );
    }

    public void completeNotice(PayoutNoticeStore.Claim claim, Instant now) {
        if (noticeSentAt == null && claim.token().equals(noticeToken)) {
            noticeSentAt = now;
            noticeToken = null;
            noticeLeaseUntil = null;
        }
    }

    public void retryNotice(PayoutNoticeStore.Claim claim, Instant now) {
        if (noticeSentAt == null && claim.token().equals(noticeToken)) {
            noticeAttempts++;
            noticeNextAttemptAt = now.plusSeconds(Math.min(3600, 30L << Math.min(noticeAttempts - 1, 7)));
            noticeToken = null;
            noticeLeaseUntil = null;
        }
    }

    public UUID getId() {
        return id;
    }

    public long getOrganizerId() {
        return organizerId;
    }

    public BigDecimal getAmountKzt() {
        return amountKzt;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
