package com.biletflow.biletflow.ordercheckout.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {
    List<OrderJpaEntity> findAllByEventId(UUID eventId);
    List<OrderJpaEntity> findAllByOwnerId(long ownerId);
}
