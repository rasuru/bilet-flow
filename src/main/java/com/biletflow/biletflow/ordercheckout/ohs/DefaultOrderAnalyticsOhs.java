package com.biletflow.biletflow.ordercheckout.ohs;

import com.biletflow.biletflow.ordercheckout.domain.order.OrderRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultOrderAnalyticsOhs implements OrderAnalyticsOhs {

    private final OrderRepository orders;

    public DefaultOrderAnalyticsOhs(OrderRepository orders) {
        this.orders = Objects.requireNonNull(orders);
    }

    @Override
    public List<OrderSnapshot> findEventOrders(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        return orders.findAllByEventId(eventId).stream().map(order -> new OrderSnapshot(
            order.getId(),
            order.getStatus().name(),
            order.getPromotionId(),
            order.getTotalPrice().amount(),
            order.getDiscountAmount().amount(),
            order.getTotalPrice().currency().getCurrencyCode(),
            order.getItems().stream().map(line -> new OrderLine(
                line.getTicketTypeId(),
                line.getPrice().amount(),
                line.getPrice().currency().getCurrencyCode()
            )).toList()
        )).toList();
    }
}
