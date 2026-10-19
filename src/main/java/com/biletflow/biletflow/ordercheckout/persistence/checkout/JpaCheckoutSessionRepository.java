package com.biletflow.biletflow.ordercheckout.persistence.checkout;

import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.entity.CheckoutJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(readOnly = true)
public class JpaCheckoutSessionRepository implements CheckoutSessionRepository {

    private final EntityManager entities;
    private final SpringDataCheckoutJpaRepository rows;
    private final CheckoutSessionPersistenceMapper mapper;

    public JpaCheckoutSessionRepository(
        EntityManager entities,
        SpringDataCheckoutJpaRepository rows,
        CheckoutSessionPersistenceMapper mapper
    ) {
        this.entities = Objects.requireNonNull(entities, "entities cannot be null");
        this.rows = Objects.requireNonNull(rows, "rows cannot be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CheckoutSession save(CheckoutSession domain) {
        var entity = entities.find(CheckoutJpaEntity.class, domain.getId().value());
        boolean created = entity == null;
        if (created) entity = mapper.toNewEntity(domain);
        else mapper.updateEntity(domain, entity);
        if (created) entities.persist(entity);
        entities.flush();
        // oc_saga references the checkout through a scalar identifier.
        return domain;
    }

    @Override
    public Optional<CheckoutSession> findById(CheckoutSessionId id) {
        return rows.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<CheckoutSession> findAllByEventId(UUID id) {
        return rows.findAllByEventId(id).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<CheckoutSession> findAllByOwnerUserId(Long id) {
        return rows.findAllByOwnerId(id).stream().map(mapper::toDomain).toList();
    }
}
