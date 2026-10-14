package com.biletflow.biletflow.ordercheckout.domain.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    /**
     * Persist using the aggregate's ID as a unique key. A checkout-derived order ID
     * must never produce a second order row. Completion retries must load an existing
     * order before calling createFromCheckout, preserving cancellation/refund state.
     */
    Order save(Order order);

    Optional<Order> findById(OrderId id);

    List<Order> findAllByEventId(UUID eventId);

    List<Order> findAllByOwnerUserId(Long ownerUserId);
}
