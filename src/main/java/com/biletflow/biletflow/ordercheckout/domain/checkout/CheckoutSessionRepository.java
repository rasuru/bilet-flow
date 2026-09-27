package com.biletflow.biletflow.ordercheckout.domain.checkout;

import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutSessionRepository {
    CheckoutSession save(CheckoutSession checkoutSession);

    Optional<CheckoutSession> findById(CheckoutSessionId id);

    List<CheckoutSession> findAllByEventId(UUID eventId);

    List<CheckoutSession> findAllByOwnerUserId(Long ownerUserId);
}
