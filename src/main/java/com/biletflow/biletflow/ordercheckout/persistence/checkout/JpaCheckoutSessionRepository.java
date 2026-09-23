package com.biletflow.biletflow.ordercheckout.persistence.checkout;

import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSession;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionId;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionRepository;
import com.biletflow.biletflow.ordercheckout.persistence.checkoutsession.entity.CheckoutSessionJpaEntity;
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
public class JpaCheckoutSessionRepository implements CheckoutSessionRepository {

    private final SpringDataCheckoutSessionJpaRepository jpaRepository;
    private final CheckoutSessionPersistenceMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaCheckoutSessionRepository(SpringDataCheckoutSessionJpaRepository jpaRepository, CheckoutSessionPersistenceMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    @Transactional
    public CheckoutSession save(CheckoutSession checkoutSession) {
        Objects.requireNonNull(checkoutSession, "checkoutSession cannot be null");

        UUID id = checkoutSession.getId().value();

        CheckoutSessionJpaEntity managed = entityManager.find(CheckoutSessionJpaEntity.class, id);

        if (managed == null) {
            CheckoutSessionJpaEntity created = mapper.toNewEntity(checkoutSession);

            entityManager.persist(created);
            entityManager.flush();

            return mapper.toDomain(created);
        }

        mapper.updateEntity(checkoutSession, managed);
        entityManager.flush();

        return mapper.toDomain(managed);
    }

    @Override
    public Optional<CheckoutSession> findById(CheckoutSessionId id) {
        Objects.requireNonNull(id, "CheckoutSessionId cannot be null");

        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<CheckoutSession> findAllByEventId(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");

        return jpaRepository.findAllByEventId(eventId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<CheckoutSession> findAllByOwnerUserId(Long ownerUserId) {
        Objects.requireNonNull(ownerUserId, "ownerUserId cannot be null");

        return jpaRepository.findAllByOwnerId(ownerUserId).stream().map(mapper::toDomain).toList();
    }
}
