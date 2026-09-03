package com.biletflow.biletflow.ticketinventory.infrastructure.persistence.tickettype;

import com.biletflow.biletflow.ticketinventory.infrastructure.persistence.tickettype.entity.TicketTypeJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataTicketTypeJpaRepository extends JpaRepository<TicketTypeJpaEntity, UUID> {
    List<TicketTypeJpaEntity> findAllByEventId(UUID eventId);
}
