package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.ordercheckout.application.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(readOnly = true)
public class JpaCheckoutProcessRepository implements CheckoutProcessRepository {

    private final EntityManager entities;
    private final CheckoutPersistenceMapper mapper;

    public JpaCheckoutProcessRepository(EntityManager entities, CheckoutPersistenceMapper mapper) {
        this.entities = entities;
        this.mapper = mapper;
    }

    @Override
    public Optional<CheckoutProcess> find(UUID id) {
        return Optional.ofNullable(entities.find(CheckoutProcessJpaEntity.class, id)).map(mapper::domain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(CheckoutProcess process) {
        var row = entities.find(CheckoutProcessJpaEntity.class, process.id());
        boolean created = row == null;
        if (created) row = new CheckoutProcessJpaEntity();
        mapper.update(process, row);
        if (created) entities.persist(row);
    }

    @Override
    public List<UUID> active() {
        return entities
            .createQuery(
                "select c.id from CheckoutJpaEntity c where c.status in ('IN_PROGRESS','AWAITING_PAYMENT') order by c.id",
                UUID.class
            )
            .getResultList();
    }
}
