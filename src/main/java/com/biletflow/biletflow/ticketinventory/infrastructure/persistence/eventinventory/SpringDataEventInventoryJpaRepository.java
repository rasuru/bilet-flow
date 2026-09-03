package com.biletflow.biletflow.ticketinventory.infrastructure.persistence.eventinventory;

import com.biletflow.biletflow.ticketinventory.infrastructure.persistence.eventinventory.entity.EventInventoryJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataEventInventoryJpaRepository extends JpaRepository<EventInventoryJpaEntity, UUID> {
    Optional<EventInventoryJpaEntity> findByEventId(UUID eventId);
}
