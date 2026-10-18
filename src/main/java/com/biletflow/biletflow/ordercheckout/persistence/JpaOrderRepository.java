package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.ordercheckout.domain.order.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(readOnly = true)
public class JpaOrderRepository implements OrderRepository {

    private final EntityManager entities;
    private final SpringDataOrderJpaRepository rows;
    private final CheckoutPersistenceMapper mapper;

    public JpaOrderRepository(EntityManager entities, SpringDataOrderJpaRepository rows, CheckoutPersistenceMapper mapper) {
        this.entities = entities;
        this.rows = rows;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Order save(Order domain) {
        var entity = entities.find(OrderJpaEntity.class, domain.getId());
        boolean created = entity == null;
        if (created) entity = new OrderJpaEntity();
        mapper.update(domain, entity);
        if (created) entities.persist(entity);
        return domain;
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return rows.findById(id.value()).map(mapper::domain);
    }

    @Override
    public List<Order> findAllByEventId(UUID id) {
        return rows.findAllByEventId(id).stream().map(mapper::domain).toList();
    }

    @Override
    public List<Order> findAllByOwnerUserId(Long id) {
        return rows.findAllByOwnerId(id).stream().map(mapper::domain).toList();
    }
}
