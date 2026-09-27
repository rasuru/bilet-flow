package com.biletflow.biletflow.ordercheckout.domain.order;

import com.biletflow.biletflow.ordercheckout.domain.order.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> findById(OrderId id);

    List<Order> findAllByEventId(UUID eventId);

    List<Order> findAllByOwnerUserId(Long ownerUserId);
}
