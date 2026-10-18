package com.biletflow.biletflow.paymentsmonetization.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "pm_refund_history")
public class RefundJpaEntity {

    @Id
    @Column(name = "id")
    UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    UUID paymentId;

    @Column(name = "reason", length = 200)
    String reason;

    @Column(name = "requested_at", nullable = false)
    Instant requestedAt;

    @Column(name = "status", nullable = false, length = 30)
    String status;

    @Column(name = "provider_reference", length = 100)
    String reference;

    @Column(name = "failure_reason", length = 200)
    String failure;

    @Column(name = "completed_at")
    Instant completedAt;

    protected RefundJpaEntity() {}
}
