package com.biletflow.biletflow.ticketinventory.infrastructure.persistence.ticket;

import com.biletflow.biletflow.ticketinventory.infrastructure.persistence.ticket.entity.TicketJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataTicketJpaRepository extends JpaRepository<TicketJpaEntity, UUID> {
    Optional<TicketJpaEntity> findByTicketCode(UUID ticketCode);

    List<TicketJpaEntity> findAllByOrderId(UUID orderId);

    List<TicketJpaEntity> findAllByEventId(UUID eventId);

    List<TicketJpaEntity> findAllByOwnerUserId(Long ownerUserId);
}
