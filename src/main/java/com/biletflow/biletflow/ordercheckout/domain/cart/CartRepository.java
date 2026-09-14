package com.biletflow.biletflow.ordercheckout.domain.cart;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository {
    Cart save(Cart cart);

    List<Cart> saveAll(List<Cart> carts);

    Optional<Cart> findById(CartId id);

    List<Cart> findAllByUserId(Long userId);

    List<Cart> findAllByEventId(UUID eventId);
}
