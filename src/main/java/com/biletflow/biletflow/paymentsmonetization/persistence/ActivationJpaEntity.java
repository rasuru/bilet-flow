package com.biletflow.biletflow.paymentsmonetization.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "pm_event_activation")
public class ActivationJpaEntity {

    @Id
    @Column(name = "event_id")
    UUID eventId;

    @Column(name = "organizer_id", nullable = false)
    long organizerId;

    @Column(name = "status", nullable = false, length = 30)
    String status;

    @Column(name = "payment_id", nullable = false)
    UUID paymentId;

    @Column(name = "fee_payment_id")
    UUID feePaymentId;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "verification_simulated", nullable = false)
    boolean verificationSimulated;

    @Column(name = "terms_version", nullable = false, length = 50)
    String termsVersion;

    @Column(name = "suspended_at")
    Instant suspendedAt;

    @Column(name = "suspension_reason", length = 200)
    String suspensionReason;

    @Version
    @Column(name = "version", nullable = false)
    long version;

    protected ActivationJpaEntity() {}
}
