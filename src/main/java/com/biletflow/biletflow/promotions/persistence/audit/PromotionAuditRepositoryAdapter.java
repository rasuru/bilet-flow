package com.biletflow.biletflow.promotions.persistence.audit;

import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PromotionAuditRepositoryAdapter implements PromotionAuditRepository {

    private final EntityManager entityManager;

    public PromotionAuditRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    public void append(PromotionAuditRecord record) {
        Objects.requireNonNull(record, "record cannot be null");

        entityManager.persist(
            new PromotionAuditEntryEntity(
                record.id(),
                record.eventId(),
                record.timestamp(),
                record.actorUserId(),
                record.type().name(),
                record.description()
            )
        );
    }
}
