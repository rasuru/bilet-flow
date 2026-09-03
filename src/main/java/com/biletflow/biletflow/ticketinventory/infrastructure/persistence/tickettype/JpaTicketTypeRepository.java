package com.biletflow.biletflow.ticketinventory.infrastructure.persistence.tickettype;

import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketType;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import com.biletflow.biletflow.ticketinventory.infrastructure.persistence.tickettype.entity.TicketTypeJpaEntity;
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
public class JpaTicketTypeRepository implements TicketTypeRepository {

    private final SpringDataTicketTypeJpaRepository jpaRepository;
    private final TicketTypePersistenceMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaTicketTypeRepository(SpringDataTicketTypeJpaRepository jpaRepository, TicketTypePersistenceMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    @Transactional
    public TicketType save(TicketType ticketType) {
        Objects.requireNonNull(ticketType, "ticketType cannot be null");

        TicketTypeJpaEntity managed = entityManager.find(TicketTypeJpaEntity.class, ticketType.getId().value());

        if (managed == null) {
            TicketTypeJpaEntity created = mapper.toNewEntity(ticketType);
            entityManager.persist(created);
            return ticketType;
        }

        mapper.updateEntity(ticketType, managed);
        return ticketType;
    }

    @Override
    public Optional<TicketType> findById(TicketTypeId id) {
        Objects.requireNonNull(id, "TicketTypeId cannot be null");
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<TicketType> findAllByEventId(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        return jpaRepository.findAllByEventId(eventId).stream().map(mapper::toDomain).toList();
    }
}
