package com.biletflow.biletflow.common.audit;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "common_financial_audit")
public class FinancialAuditJpaEntity {

    @Id
    @Column(name = "id")
    UUID id;

    @Column(name = "context", nullable = false, length = 40)
    String context;

    @Column(name = "aggregate_id", nullable = false)
    UUID aggregateId;

    @Column(name = "actor_id")
    Long actor;

    @Column(name = "action", nullable = false, length = 120)
    String action;

    @Column(name = "occurred_at", nullable = false)
    Instant occurredAt;

    @Column(name = "details", length = 200)
    String details;

    protected FinancialAuditJpaEntity() {}
}
