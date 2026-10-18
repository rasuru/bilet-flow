package com.biletflow.biletflow.paymentsmonetization.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "pm_ledger")
public class LedgerEntryJpaEntity {

    @Id
    @Column(name = "operation_key")
    String id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    UUID paymentId;

    @Column(name = "event_id", nullable = false, updatable = false)
    UUID eventId;

    @Column(name = "organizer_id", nullable = false, updatable = false)
    long organizerId;

    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    String kind;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    BigDecimal amount;

    @Column(name = "fee", nullable = false, precision = 19, scale = 2, updatable = false)
    BigDecimal fee;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    protected LedgerEntryJpaEntity() {}
}
