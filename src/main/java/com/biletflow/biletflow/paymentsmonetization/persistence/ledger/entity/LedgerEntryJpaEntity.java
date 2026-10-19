package com.biletflow.biletflow.paymentsmonetization.persistence.ledger.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "pm_ledger")
public class LedgerEntryJpaEntity {

    @Id
    @Column(name = "operation_key", nullable = false, updatable = false, length = 100)
    private String id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "organizer_id", nullable = false, updatable = false)
    private long organizerId;

    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    @Enumerated(EnumType.STRING)
    private LedgerEntryKind kind;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "fee", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal fee;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntryJpaEntity() {}

    public LedgerEntryJpaEntity(
        String id,
        UUID paymentId,
        UUID eventId,
        long organizerId,
        LedgerEntryKind kind,
        BigDecimal amount,
        BigDecimal fee,
        Instant createdAt
    ) {
        this.id = id;
        this.paymentId = paymentId;
        this.eventId = eventId;
        this.organizerId = organizerId;
        this.kind = kind;
        this.amount = amount;
        this.fee = fee;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public long getOrganizerId() {
        return organizerId;
    }

    public LedgerEntryKind getKind() {
        return kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
