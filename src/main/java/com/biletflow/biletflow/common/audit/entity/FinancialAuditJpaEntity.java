package com.biletflow.biletflow.common.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "common_financial_audit")
public class FinancialAuditJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "context", nullable = false, length = 40)
    private String context;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "actor_id")
    private Long actor;

    @Column(name = "action", nullable = false, length = 120)
    private String action;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "details", length = 200)
    private String details;

    protected FinancialAuditJpaEntity() {}

    public FinancialAuditJpaEntity(
        UUID id,
        String context,
        UUID aggregateId,
        Long actor,
        String action,
        Instant occurredAt,
        String details
    ) {
        this.id = id;
        this.context = context;
        this.aggregateId = aggregateId;
        this.actor = actor;
        this.action = action;
        this.occurredAt = occurredAt;
        this.details = details;
    }
}
