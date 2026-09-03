package com.biletflow.biletflow.ticketinventory.infrastructure.persistence.ticket;

import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.ticket.*;
import com.biletflow.biletflow.ticketinventory.infrastructure.persistence.ticket.entity.TicketJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaTicketRepository implements TicketRepository {

    private final SpringDataTicketJpaRepository jpaRepository;
    private final TicketPersistenceMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaTicketRepository(SpringDataTicketJpaRepository jpaRepository, TicketPersistenceMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    @Transactional
    public Ticket save(Ticket ticket) {
        Objects.requireNonNull(ticket, "ticket cannot be null");

        TicketJpaEntity managed = entityManager.find(TicketJpaEntity.class, ticket.getId().value());

        if (managed == null) {
            TicketJpaEntity created = mapper.toNewEntity(ticket);
            entityManager.persist(created);
            return ticket;
        }

        mapper.updateEntity(ticket, managed);
        return ticket;
    }

    @Override
    @Transactional
    public List<Ticket> saveAll(List<Ticket> tickets) {
        Objects.requireNonNull(tickets, "tickets cannot be null");
        return tickets.stream().map(this::save).toList();
    }

    @Override
    public Optional<Ticket> findById(TicketId id) {
        Objects.requireNonNull(id, "TicketId cannot be null");
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Ticket> findByTicketCode(TicketCode ticketCode) {
        Objects.requireNonNull(ticketCode, "TicketCode cannot be null");
        return jpaRepository.findByTicketCode(ticketCode.value()).map(mapper::toDomain);
    }

    @Override
    public List<Ticket> findAllByOrderId(OrderId orderId) {
        Objects.requireNonNull(orderId, "OrderId cannot be null");
        return jpaRepository.findAllByOrderId(orderId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Ticket> findAllByEventId(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        return jpaRepository.findAllByEventId(eventId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Ticket> findAllByOwnerUserId(Long ownerUserId) {
        Objects.requireNonNull(ownerUserId, "ownerUserId cannot be null");
        return jpaRepository.findAllByOwnerUserId(ownerUserId).stream().map(mapper::toDomain).toList();
    }
}
