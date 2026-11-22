package com.biletflow.biletflow.support.persistence.audit;

import com.biletflow.biletflow.support.application.audit.SupportAuditRepository;
import jakarta.persistence.EntityManager;
import java.util.Objects;
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
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(SupportAuditRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        entities.persist(new SupportAuditJpaEntity(
            record.id(), record.caseId(), record.eventId(), record.occurredAt(),
            record.actorUserId(), record.action(), record.details()
        ));
    }
}
