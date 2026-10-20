package com.biletflow.biletflow.ordercheckout.persistence.checkout;

import com.biletflow.biletflow.ordercheckout.persistence.checkout.entity.CheckoutJpaEntity;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCheckoutJpaRepository extends JpaRepository<CheckoutJpaEntity, UUID> {
    List<CheckoutJpaEntity> findAllByEventId(UUID eventId);
    List<CheckoutJpaEntity> findAllByOwnerId(long ownerId);
}
