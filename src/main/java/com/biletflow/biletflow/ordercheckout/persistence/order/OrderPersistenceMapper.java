package com.biletflow.biletflow.ordercheckout.persistence.order;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.domain.order.AssignedSeatingOrderItem;
import com.biletflow.biletflow.ordercheckout.domain.order.GeneralAdmissionOrderItem;
import com.biletflow.biletflow.ordercheckout.domain.order.Order;
import com.biletflow.biletflow.ordercheckout.domain.order.OrderId;
import com.biletflow.biletflow.ordercheckout.domain.order.OrderItemMode;
import com.biletflow.biletflow.ordercheckout.persistence.order.entity.OrderItemJpaEmbeddable;
import com.biletflow.biletflow.ordercheckout.persistence.order.entity.OrderJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Currency;
import java.util.List;

@Component
public class OrderPersistenceMapper {

    public Order toDomain(OrderJpaEntity entity) {
        Currency currency = Currency.getInstance(entity.getPriceCurrency());

        List<OrderItemMode> items = entity.getItems()
            .stream()
            .map(item -> toDomainItem(item, currency))
            .toList();

        return new Order(
            new OrderId(entity.getId()),
            entity.getStatus(),
            entity.getOwnerId(),
            entity.getEventId(),
            entity.getEventStartTime(),
            entity.getEventEndTime(),
            entity.getInventoryMode(),
            items,
            entity.getPromotionId(),
            new Money(entity.getDiscountAmount(), currency),
            new Money(entity.getTotalPrice(), currency),
            new Money(entity.getFinalPrice(), currency),
            entity.getPaymentId(),
            entity.getCancellationReason(),
            entity.getRefundId()
        );
    }

    private OrderItemMode toDomainItem(
        OrderItemJpaEmbeddable item,
        Currency currency
    ) {
        Money price = new Money(item.getPrice(), currency);

        if (item.getSeatId() != null) {
            AssignedSeatingOrderItem orderItem =
                new AssignedSeatingOrderItem(
                    item.getTicketTypeId(),
                    item.getSeatId(),
                    price
                );

            if (item.isUsed()) {
                orderItem.markAsUsed();
            }

            return orderItem;
        }

        GeneralAdmissionOrderItem orderItem =
            new GeneralAdmissionOrderItem(
                item.getTicketTypeId(),
                price
            );

        if (item.isUsed()) {
            orderItem.markAsUsed();
        }

        return orderItem;
    }

    public OrderJpaEntity toNewEntity(Order domain) {
        return new OrderJpaEntity(
            domain.getId(),
            domain.getStatus(),
            domain.getOwnerId(),
            domain.getEventId(),
            domain.getEventStartTime(),
            domain.getEventEndTime(),
            domain.getInventoryMode(),
            domain.getPromotionId(),
            domain.getDiscountAmount().amount(),
            domain.getTotalPrice().amount(),
            domain.getFinalPrice().amount(),
            domain.getFinalPrice().currency().getCurrencyCode(),
            domain.getPaymentId(),
            domain.getCancellationReason(),
            domain.getRefundId(),
            toJpaItems(domain)
        );
    }

    private List<OrderItemJpaEmbeddable> toJpaItems(Order domain) {
        return domain.getItems()
            .stream()
            .map(this::toJpaItem)
            .toList();
    }

    private OrderItemJpaEmbeddable toJpaItem(OrderItemMode item) {
        if (item instanceof AssignedSeatingOrderItem assigned) {
            return new OrderItemJpaEmbeddable(
                assigned.getTicketTypeId(),
                assigned.getSeatId(),
                assigned.getPrice().amount(),
                assigned.isUsed()
            );
        }

        if (item instanceof GeneralAdmissionOrderItem general) {
            return new OrderItemJpaEmbeddable(
                general.getTicketTypeId(),
                null,
                general.getPrice().amount(),
                general.isUsed()
            );
        }

        throw new IllegalArgumentException(
            "Unknown order item type: " + item.getClass()
        );
    }

    public void updateEntity(
        Order domain,
        OrderJpaEntity entity
    ) {
        // TODO
    }
}
