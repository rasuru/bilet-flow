package com.biletflow.biletflow.ordercheckout.ohs;

import com.biletflow.biletflow.ordercheckout.application.CheckoutProcessRepository;
import com.biletflow.biletflow.ordercheckout.domain.order.OrderId;
import com.biletflow.biletflow.ordercheckout.domain.order.OrderRepository;
import com.biletflow.biletflow.ordercheckout.ohs.dto.SupportOrderReference;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultOrderCheckoutOhs implements OrderCheckoutOhs {
    private final OrderRepository orders;

    private final CheckoutProcessRepository checkouts;

    public DefaultOrderCheckoutOhs(OrderRepository orders, CheckoutProcessRepository checkouts) {
        this.orders = Objects.requireNonNull(orders);
        this.checkouts = Objects.requireNonNull(checkouts);
    }

    @Override
    public Optional<SupportOrderReference> findSupportReference(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        return orders.findById(new OrderId(orderId))
            .map(order -> new SupportOrderReference(order.getId(), order.getOwnerId(), order.getEventId()))
            .or(() -> checkouts.find(orderId)
                .map(checkout -> new SupportOrderReference(checkout.id(), checkout.ownerId(), checkout.eventId())));
    }
}
