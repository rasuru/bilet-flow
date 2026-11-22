package com.biletflow.biletflow.ordercheckout.ohs;

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

    public DefaultOrderCheckoutOhs(OrderRepository orders) {
        this.orders = Objects.requireNonNull(orders);
    }

    @Override
    public Optional<SupportOrderReference> findSupportReference(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        return orders.findById(new OrderId(orderId)).map(order ->
            new SupportOrderReference(order.getId(), order.getOwnerId(), order.getEventId()));
    }
}
