package com.biletflow.biletflow.ordercheckout.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCheckoutJpaRepository extends JpaRepository<CheckoutJpaEntity, UUID> {
    List<CheckoutJpaEntity> findAllByEventId(UUID eventId);
    List<CheckoutJpaEntity> findAllByOwnerId(long ownerId);
}
