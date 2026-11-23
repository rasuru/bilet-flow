package com.biletflow.biletflow.ticketinventory.persistence.audit;

import com.biletflow.biletflow.ticketinventory.application.audit.TicketInventoryAuditRepository;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class TicketInventoryAuditRepositoryAdapter implements TicketInventoryAuditRepository {

    private final EntityManager entityManager;

    public TicketInventoryAuditRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    public void append(TicketInventoryAuditRecord record) {
        Objects.requireNonNull(record, "record cannot be null");

        entityManager.persist(
            new TicketInventoryAuditEntryEntity(
                record.id(),
                record.eventId(),
                record.timestamp(),
                record.actorUserId(),
                record.type().name(),
                record.entityId(),
                record.description()
            )
        );
    }
}
