package com.biletflow.biletflow.ticketinventory.infrastructure.persistence.eventinventory;

import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventoryId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventoryRepository;
import com.biletflow.biletflow.ticketinventory.infrastructure.persistence.eventinventory.entity.EventInventoryJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaEventInventoryRepository implements EventInventoryRepository {

    private final SpringDataEventInventoryJpaRepository jpaRepository;
    private final EventInventoryPersistenceMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaEventInventoryRepository(SpringDataEventInventoryJpaRepository jpaRepository, EventInventoryPersistenceMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    @Transactional
    public EventInventory save(EventInventory eventInventory) {
        Objects.requireNonNull(eventInventory, "eventInventory cannot be null");

        UUID id = eventInventory.getId().value();
        EventInventoryJpaEntity managed = entityManager.find(EventInventoryJpaEntity.class, id);

        if (managed == null) {
            EventInventoryJpaEntity created = mapper.toNewEntity(eventInventory);
            entityManager.persist(created);
            entityManager.flush();
            return mapper.toDomain(created);
        }

        mapper.updateEntity(eventInventory, managed);
        entityManager.flush();
        return mapper.toDomain(managed);
    }

    @Override
    public Optional<EventInventory> findById(EventInventoryId id) {
        Objects.requireNonNull(id, "EventInventoryId cannot be null");
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<EventInventory> findByEventId(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        return jpaRepository.findByEventId(eventId).map(mapper::toDomain);
    }
}
