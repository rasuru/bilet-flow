package com.biletflow.biletflow.ticketinventory.persistence.ticket;

import com.biletflow.biletflow.ticketinventory.persistence.ticket.entity.TicketJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataTicketJpaRepository extends JpaRepository<TicketJpaEntity, UUID> {
    Optional<TicketJpaEntity> findByTicketCode(UUID ticketCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketJpaEntity t where t.ticketCode = :code")
    Optional<TicketJpaEntity> findByTicketCodeForUpdate(@Param("code") UUID code);

    List<TicketJpaEntity> findAllByOrderId(UUID orderId);

    List<TicketJpaEntity> findAllByEventId(UUID eventId);

    List<TicketJpaEntity> findAllByOwnerUserId(Long ownerUserId);
}
