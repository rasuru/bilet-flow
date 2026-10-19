package com.biletflow.biletflow.ordercheckout.persistence.order;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import com.biletflow.biletflow.ordercheckout.domain.order.*;
import com.biletflow.biletflow.ordercheckout.persistence.common.entity.PurchaseItemJpaEmbeddable;
import com.biletflow.biletflow.ordercheckout.persistence.order.entity.OrderJpaEntity;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OrderPersistenceMapper {

    public OrderJpaEntity toNewEntity(Order domain) {
        return new OrderJpaEntity(
            domain.getId(),
            domain.getEventId(),
            domain.getOwnerId(),
            domain.getStatus(),
            domain.getInventoryMode(),
            domain.getPromotionId(),
            domain.getDiscountAmount().amount(),
            domain.getDiscountAmount().currency().getCurrencyCode(),
            domain.getTotalPrice().amount(),
            domain.getTotalPrice().currency().getCurrencyCode(),
            domain.getPaymentId(),
            domain.getCancellationReason() == null ? null : domain.getCancellationReason(),
            domain.getRefundId(),
            domain
                .getItems()
                .stream()
                .map(item ->
                    new PurchaseItemJpaEmbeddable(
                        item.getTicketTypeId(),
                        item instanceof AssignedSeatingOrderItem assigned ? assigned.getSeatId() : null,
                        item.getPrice()
                    )
                )
                .toList()
        );
    }

    public void updateEntity(Order domain, OrderJpaEntity entity) {
        if (!entity.getId().equals(domain.getId())) throw new IllegalArgumentException("Cannot map onto another Order id");
        entity.updateMutableState(
            domain.getStatus(),
            domain.getCancellationReason() == null ? null : domain.getCancellationReason(),
            domain.getRefundId()
        );
    }

    public Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity
            .getItems()
            .stream()
            .map(item ->
                item.getSeatId() == null
                    ? (OrderItem) new GeneralAdmissionOrderItem(item.getTicketTypeId(), item.getPrice())
                    : new AssignedSeatingOrderItem(item.getTicketTypeId(), item.getSeatId(), item.getPrice())
            )
            .toList();
        return new Order(
            new OrderId(entity.getId()),
            entity.getStatus(),
            entity.getOwnerId(),
            entity.getEventId(),
            entity.getMode(),
            items,
            entity.getPromotionId(),
            Money.of(entity.getDiscount(), Currency.getInstance(entity.getDiscountCurrency())),
            Money.of(entity.getTotal(), Currency.getInstance(entity.getTotalCurrency())),
            entity.getPaymentId(),
            entity.getReason() == null ? null : entity.getReason(),
            entity.getRefundId()
        );
    }
}
