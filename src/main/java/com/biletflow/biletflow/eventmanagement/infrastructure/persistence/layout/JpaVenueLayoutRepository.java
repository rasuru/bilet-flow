package com.biletflow.biletflow.eventmanagement.infrastructure.persistence.layout;

import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.infrastructure.persistence.layout.entity.VenueLayoutJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaVenueLayoutRepository implements VenueLayoutRepository {

    private final SpringDataVenueLayoutJpaRepository springDataRepository;
    private final VenueLayoutPersistenceMapper mapper;
    private final EntityManager entityManager;

    public JpaVenueLayoutRepository(
        SpringDataVenueLayoutJpaRepository springDataRepository,
        VenueLayoutPersistenceMapper mapper,
        EntityManager entityManager
    ) {
        this.springDataRepository = Objects.requireNonNull(springDataRepository);
        this.mapper = Objects.requireNonNull(mapper);
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    public VenueLayout save(VenueLayout layout) {
        Objects.requireNonNull(layout, "layout cannot be null");

        VenueLayoutJpaEntity managed = entityManager.find(VenueLayoutJpaEntity.class, layout.getId().value());

        if (managed == null) {
            VenueLayoutJpaEntity created = mapper.toNewEntity(layout);

            entityManager.persist(created);
            entityManager.flush();

            return mapper.toDomain(created);
        }

        mapper.updateEntity(layout, managed);
        entityManager.flush();

        return mapper.toDomain(managed);
    }

    @Override
    public Optional<VenueLayout> findById(VenueLayoutId id) {
        Objects.requireNonNull(id, "id cannot be null");

        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<VenueLayout> findAll() {
        return springDataRepository.findAll().stream().map(mapper::toDomain).toList();
    }
}
