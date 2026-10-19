package com.biletflow.biletflow.paymentsmonetization.persistence.payment.entity;

import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.RefundStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "pm_refund_history")
public class RefundJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private RefundStatus status;

    @Column(name = "provider_reference", length = 100)
    private String reference;

    @Column(name = "failure_reason", length = 200)
    private String failure;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected RefundJpaEntity() {}

    public RefundJpaEntity(
        UUID id,
        UUID paymentId,
        String reason,
        Instant requestedAt,
        RefundStatus status,
        String reference,
        String failure,
        Instant completedAt
    ) {
        this.id = id;
        this.paymentId = paymentId;
        this.reason = reason;
        this.requestedAt = requestedAt;
        this.status = status;
        this.reference = reference;
        this.failure = failure;
        this.completedAt = completedAt;
    }

    public void updateMutableState(RefundStatus status, String reference, String failure, Instant completedAt) {
        this.status = status;
        this.reference = reference;
        this.failure = failure;
        this.completedAt = completedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getReference() {
        return reference;
    }

    public String getFailure() {
        return failure;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
