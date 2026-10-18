package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(readOnly = true)
public class JpaCheckoutSessionRepository implements CheckoutSessionRepository {

    private final EntityManager entities;
    private final SpringDataCheckoutJpaRepository rows;
    private final CheckoutPersistenceMapper mapper;

    public JpaCheckoutSessionRepository(EntityManager entities, SpringDataCheckoutJpaRepository rows, CheckoutPersistenceMapper mapper) {
        this.entities = entities;
        this.rows = rows;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CheckoutSession save(CheckoutSession domain) {
        var entity = entities.find(CheckoutJpaEntity.class, domain.getId().value());
        boolean created = entity == null;
        if (created) entity = new CheckoutJpaEntity();
        mapper.update(domain, entity);
        if (created) entities.persist(entity);
        entities.flush();
        // oc_saga references the checkout through a scalar identifier.
        return domain;
    }

    @Override
    public Optional<CheckoutSession> findById(CheckoutSessionId id) {
        return rows.findById(id.value()).map(mapper::domain);
    }

    @Override
    public List<CheckoutSession> findAllByEventId(UUID id) {
        return rows.findAllByEventId(id).stream().map(mapper::domain).toList();
    }

    @Override
    public List<CheckoutSession> findAllByOwnerUserId(Long id) {
        return rows.findAllByOwnerId(id).stream().map(mapper::domain).toList();
    }
}
