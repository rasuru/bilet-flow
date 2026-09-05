package com.biletflow.biletflow.eventmanagement.persistence.audit;

import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class EventAuditRepositoryAdapter implements EventAuditRepository {

    private final EntityManager entityManager;

    public EventAuditRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void append(EventAuditRecord record) {
        EventAuditEntryEntity entity = new EventAuditEntryEntity(
            record.id(),
            record.eventId(),
            record.timestamp(),
            record.actorUserId(),
            record.type().name(),
            record.description()
        );

        entityManager.persist(entity);
    }
}
