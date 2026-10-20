package com.biletflow.biletflow.ordercheckout.persistence.checkout;

import com.biletflow.biletflow.ordercheckout.application.*;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.entity.CheckoutJpaEntity;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.entity.CheckoutProcessJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(readOnly = true)
public class JpaCheckoutProcessRepository implements CheckoutProcessRepository {

    private final EntityManager entities;
    private final CheckoutProcessPersistenceMapper mapper;

    public JpaCheckoutProcessRepository(EntityManager entities, CheckoutProcessPersistenceMapper mapper) {
        this.entities = Objects.requireNonNull(entities, "entities cannot be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
    }

    @Override
    public Optional<CheckoutProcess> find(UUID id) {
        return Optional.ofNullable(entities.find(CheckoutProcessJpaEntity.class, id)).map(mapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(CheckoutProcess process) {
        var row = entities.find(CheckoutProcessJpaEntity.class, process.id());
        boolean created = row == null;
        if (created) row = mapper.toNewEntity(process);
        else mapper.updateEntity(process, row);
        if (created) entities.persist(row);
    }

    @Override
    public List<UUID> active() {
        return entities
            .createQuery(
                "select c.id from CheckoutJpaEntity c where c.status in (com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionStatus.IN_PROGRESS, com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionStatus.AWAITING_PAYMENT) order by c.id",
                UUID.class
            )
            .getResultList();
    }
}
