package com.biletflow.biletflow.ordercheckout.persistence.order;

import com.biletflow.biletflow.ordercheckout.domain.order.Order;
import com.biletflow.biletflow.ordercheckout.domain.order.OrderId;
import com.biletflow.biletflow.ordercheckout.domain.order.OrderRepository;
import com.biletflow.biletflow.ordercheckout.persistence.order.entity.OrderJpaEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class JpaOrderRepository implements OrderRepository {

    private final SpringDataOrderJpaRepository jpaRepository;
    private final OrderPersistenceMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaOrderRepository(
            SpringDataOrderJpaRepository jpaRepository,
            OrderPersistenceMapper mapper
    ) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    @Transactional
    public Order save(Order order) {
        Objects.requireNonNull(order, "order cannot be null");

        UUID id = order.getId();

        OrderJpaEntity managed =
                entityManager.find(OrderJpaEntity.class, id);

        if (managed == null) {
            OrderJpaEntity created = mapper.toNewEntity(order);

            entityManager.persist(created);
            entityManager.flush();

            return mapper.toDomain(created);
        }

        mapper.updateEntity(order, managed);
        entityManager.flush();

        return mapper.toDomain(managed);
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        Objects.requireNonNull(id, "OrderId cannot be null");

        return jpaRepository
                .findById(id.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<Order> findAllByEventId(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");

        return jpaRepository
                .findAllByEventId(eventId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Order> findAllByOwnerUserId(Long ownerUserId) {
        Objects.requireNonNull(
                ownerUserId,
                "ownerUserId cannot be null"
        );

        return jpaRepository
                .findAllByOwnerId(ownerUserId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
