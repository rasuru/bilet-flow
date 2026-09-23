package com.biletflow.biletflow.ordercheckout.persistence.checkout;

import com.biletflow.biletflow.ordercheckout.persistence.checkoutsession.entity.CheckoutSessionJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCheckoutSessionJpaRepository
        extends JpaRepository<CheckoutSessionJpaEntity, UUID> {

    List<CheckoutSessionJpaEntity> findAllByEventId(UUID eventId);

    List<CheckoutSessionJpaEntity> findAllByOwnerId(Long ownerId);
}
