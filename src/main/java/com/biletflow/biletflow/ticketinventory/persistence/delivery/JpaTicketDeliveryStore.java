package com.biletflow.biletflow.ticketinventory.persistence.delivery;

import com.biletflow.biletflow.ticketinventory.application.delivery.TicketDeliveryStore;
import com.biletflow.biletflow.ticketinventory.persistence.delivery.entity.TicketDeliveryJpaEntity;
import com.biletflow.biletflow.ticketinventory.persistence.ticket.entity.TicketJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaTicketDeliveryStore implements TicketDeliveryStore {

    private final EntityManager entities;

    public JpaTicketDeliveryStore(EntityManager entities) {
        this.entities = Objects.requireNonNull(entities);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(List<UUID> ticketIds, Instant now) {
        // Ensure tickets exist before queue inserts, including with Hibernate's ordered inserts enabled.
        entities.flush();
        for (UUID ticketId : ticketIds) {
            if (entities.find(TicketDeliveryJpaEntity.class, ticketId) == null) entities.persist(
                new TicketDeliveryJpaEntity(ticketId, now)
            );
        }
    }

    @Override
    public void request(UUID ticketId, Instant now) {
        // Lock the parent even when the queue row is absent (tickets issued before delivery was introduced).
        if (entities.find(TicketJpaEntity.class, ticketId, LockModeType.PESSIMISTIC_WRITE) == null) throw new IllegalStateException(
            "Ticket is missing"
        );
        var row = entities.find(TicketDeliveryJpaEntity.class, ticketId);
        if (row == null) entities.persist(new TicketDeliveryJpaEntity(ticketId, now));
        else row.request(now);
    }

    @Override
    public Optional<Claim> claim(Instant now) {
        var rows = entities
            .createQuery(
                "select d from TicketDeliveryJpaEntity d where d.state = :pending and d.nextAttemptAt <= :now and (d.leaseUntil is null or d.leaseUntil <= :now) order by d.nextAttemptAt, d.ticketId",
                TicketDeliveryJpaEntity.class
            )
            .setParameter("pending", TicketDeliveryJpaEntity.State.PENDING)
            .setParameter("now", now)
            .setMaxResults(1)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
        return rows
            .stream()
            .findFirst()
            .map(row -> row.claim(now));
    }

    @Override
    public void complete(Claim claim, boolean skipped, Instant now) {
        var row = entities.find(TicketDeliveryJpaEntity.class, claim.ticketId(), LockModeType.PESSIMISTIC_WRITE);
        if (row != null) row.complete(claim, skipped, now);
    }

    @Override
    public void retry(Claim claim, Instant now) {
        var row = entities.find(TicketDeliveryJpaEntity.class, claim.ticketId(), LockModeType.PESSIMISTIC_WRITE);
        if (row != null) row.retry(claim, now);
    }
}
