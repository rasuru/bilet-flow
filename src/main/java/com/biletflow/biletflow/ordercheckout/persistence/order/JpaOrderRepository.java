package com.biletflow.biletflow.ordercheckout.persistence.order;

import com.biletflow.biletflow.ordercheckout.domain.order.*;
import com.biletflow.biletflow.ordercheckout.persistence.order.entity.OrderJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(readOnly = true)
public class JpaOrderRepository implements OrderRepository {

    private final EntityManager entities;
    private final SpringDataOrderJpaRepository rows;
    private final OrderPersistenceMapper mapper;

    public JpaOrderRepository(EntityManager entities, SpringDataOrderJpaRepository rows, OrderPersistenceMapper mapper) {
        this.entities = Objects.requireNonNull(entities, "entities cannot be null");
        this.rows = Objects.requireNonNull(rows, "rows cannot be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Order save(Order domain) {
        var entity = entities.find(OrderJpaEntity.class, domain.getId());
        boolean created = entity == null;
        if (created) entity = mapper.toNewEntity(domain);
        else mapper.updateEntity(domain, entity);
        if (created) entities.persist(entity);
        return domain;
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return rows.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Order> findAllByEventId(UUID id) {
        return rows.findAllByEventId(id).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Order> findAllByOwnerUserId(Long id) {
        return rows.findAllByOwnerId(id).stream().map(mapper::toDomain).toList();
    }
}
