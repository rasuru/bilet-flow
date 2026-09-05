package com.biletflow.biletflow.eventmanagement.persistence.event;

import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.persistence.event.entity.SocialEventJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSocialEventRepository implements SocialEventRepository {

    private final SpringDataSocialEventJpaRepository springDataRepository;
    private final SocialEventPersistenceMapper mapper;
    private final EntityManager entityManager;

    public JpaSocialEventRepository(
        SpringDataSocialEventJpaRepository springDataRepository,
        SocialEventPersistenceMapper mapper,
        EntityManager entityManager
    ) {
        this.springDataRepository = Objects.requireNonNull(springDataRepository);
        this.mapper = Objects.requireNonNull(mapper);
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    public SocialEvent save(SocialEvent socialEvent) {
        Objects.requireNonNull(socialEvent, "socialEvent cannot be null");

        SocialEventJpaEntity managed = entityManager.find(SocialEventJpaEntity.class, socialEvent.getId().value());

        if (managed == null) {
            SocialEventJpaEntity created = mapper.toNewEntity(socialEvent);

            entityManager.persist(created);
            entityManager.flush();

            return mapper.toDomain(created);
        }

        mapper.updateEntity(socialEvent, managed);
        entityManager.flush();

        return mapper.toDomain(managed);
    }

    @Override
    public Optional<SocialEvent> findById(SocialEventId id) {
        Objects.requireNonNull(id, "id cannot be null");

        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<SocialEvent> findByOrganizerId(Long organizerId) {
        Objects.requireNonNull(organizerId, "organizerId cannot be null");

        return springDataRepository.findAllByOrganizerId(organizerId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<SocialEvent> findByStatusAndVisibility(SocialEventStatus status, EventVisibility visibility) {
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(visibility, "visibility cannot be null");

        return springDataRepository.findAllByStatusAndVisibility(status, visibility).stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsById(SocialEventId id) {
        Objects.requireNonNull(id, "id cannot be null");

        return springDataRepository.existsById(id.value());
    }

    @Override
    public void deleteById(SocialEventId id) {
        Objects.requireNonNull(id, "id cannot be null");

        springDataRepository.deleteById(id.value());
    }
}
