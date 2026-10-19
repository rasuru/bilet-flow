package com.biletflow.biletflow.common.audit;

import com.biletflow.biletflow.common.audit.entity.FinancialAuditJpaEntity;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class FinancialAudit implements com.biletflow.biletflow.common.application.FinancialAudit {

    private final EntityManager entities;
    private final Clock clock;

    public FinancialAudit(EntityManager entities, Clock clock) {
        this.entities = Objects.requireNonNull(entities, "entities cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String context, UUID aggregateId, Long actor, String action) {
        recordDetails(context, aggregateId, actor, action, null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDetails(String context, UUID aggregateId, Long actor, String action, String details) {
        entities.persist(new FinancialAuditJpaEntity(UUID.randomUUID(), context, aggregateId, actor, action, clock.instant(), details));
    }
}
