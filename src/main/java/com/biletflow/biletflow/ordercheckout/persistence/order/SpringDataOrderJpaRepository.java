package com.biletflow.biletflow.ordercheckout.persistence.order;

import com.biletflow.biletflow.ordercheckout.persistence.order.entity.OrderJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOrderJpaRepository
        extends JpaRepository<OrderJpaEntity, UUID> {

    List<OrderJpaEntity> findAllByEventId(UUID eventId);

    List<OrderJpaEntity> findAllByOwnerId(Long ownerId);
}
