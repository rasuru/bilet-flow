package com.biletflow.biletflow.support.persistence.audit;

import com.biletflow.biletflow.support.application.audit.SupportAuditRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaSupportAuditRepository implements SupportAuditRepository {
    private final EntityManager entities;

    public JpaSupportAuditRepository(EntityManager entities) {
        this.entities = Objects.requireNonNull(entities);
    }

    @Override
    public List<SupportAuditRecord> findByCaseId(UUID caseId) {
        return entities.createQuery("select a from SupportAuditJpaEntity a where a.caseId = :caseId order by a.occurredAt, a.id",
            SupportAuditJpaEntity.class).setParameter("caseId", caseId).getResultList().stream()
            .map(SupportAuditJpaEntity::toRecord).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(SupportAuditRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        entities.persist(new SupportAuditJpaEntity(
            record.id(), record.caseId(), record.eventId(), record.occurredAt(),
            record.actorUserId(), record.action(), record.details()
        ));
    }
}

