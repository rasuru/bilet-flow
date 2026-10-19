package com.biletflow.biletflow.paymentsmonetization.persistence.activation.entity;

import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivationStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "pm_event_activation")
public class ActivationJpaEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "organizer_id", nullable = false)
    private long organizerId;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private EventPaidSalesActivationStatus status;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "fee_payment_id")
    private UUID feePaymentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "verification_simulated", nullable = false)
    private boolean verificationSimulated;

    @Column(name = "terms_version", nullable = false, length = 50)
    private String termsVersion;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspension_reason", length = 200)
    private String suspensionReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ActivationJpaEntity() {}

    public ActivationJpaEntity(
        UUID eventId,
        long organizerId,
        EventPaidSalesActivationStatus status,
        UUID paymentId,
        UUID feePaymentId,
        Instant createdAt,
        boolean verificationSimulated,
        String termsVersion,
        Instant suspendedAt,
        String suspensionReason
    ) {
        this.eventId = eventId;
        this.organizerId = organizerId;
        this.status = status;
        this.paymentId = paymentId;
        this.feePaymentId = feePaymentId;
        this.createdAt = createdAt;
        this.verificationSimulated = verificationSimulated;
        this.termsVersion = termsVersion;
        this.suspendedAt = suspendedAt;
        this.suspensionReason = suspensionReason;
    }

    public void updateMutableState(
        EventPaidSalesActivationStatus status,
        UUID paymentId,
        UUID feePaymentId,
        Instant createdAt,
        boolean verificationSimulated,
        String termsVersion,
        Instant suspendedAt,
        String suspensionReason
    ) {
        this.status = status;
        this.paymentId = paymentId;
        this.feePaymentId = feePaymentId;
        this.createdAt = createdAt;
        this.verificationSimulated = verificationSimulated;
        this.termsVersion = termsVersion;
        this.suspendedAt = suspendedAt;
        this.suspensionReason = suspensionReason;
    }

    public UUID getEventId() {
        return eventId;
    }

    public long getOrganizerId() {
        return organizerId;
    }

    public EventPaidSalesActivationStatus getStatus() {
        return status;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getFeePaymentId() {
        return feePaymentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isVerificationSimulated() {
        return verificationSimulated;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }

    public long getVersion() {
        return version;
    }
}
