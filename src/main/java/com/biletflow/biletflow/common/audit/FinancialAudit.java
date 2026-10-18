package com.biletflow.biletflow.common.audit;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class FinancialAudit implements com.biletflow.biletflow.common.application.FinancialAudit {

    private final EntityManager entities;
    private final Clock clock;

    public FinancialAudit(EntityManager entities, Clock clock) {
        this.entities = entities;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String context, UUID aggregateId, Long actor, String action) {
        recordDetails(context, aggregateId, actor, action, null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDetails(String context, UUID aggregateId, Long actor, String action, String details) {
        var row = new FinancialAuditJpaEntity();
        row.id = UUID.randomUUID();
        row.context = context;
        row.aggregateId = aggregateId;
        row.actor = actor;
        row.action = action;
        row.occurredAt = clock.instant();
        row.details = details;
        entities.persist(row);
    }
}
