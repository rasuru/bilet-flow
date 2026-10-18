package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.application.CheckoutProcess;
import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import com.biletflow.biletflow.ordercheckout.domain.order.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CheckoutPersistenceMapper {

    public void update(CheckoutSession domain, CheckoutJpaEntity entity) {
        entity.id = domain.getId().value();
        entity.eventId = domain.getEventId();
        entity.ownerId = domain.getOwnerId();
        entity.status = domain.getStatus().name();
        entity.mode = domain.getInventoryMode().name();
        entity.promotionId = domain.getPromotionId();
        entity.discount = domain.getDiscountAmount().amount();
        entity.discountCurrency = domain.getDiscountAmount().currency().getCurrencyCode();
        entity.total = domain.getTotalPrice().amount();
        entity.totalCurrency = domain.getTotalPrice().currency().getCurrencyCode();
        entity.paymentId = domain.getPaymentId();
        entity.items.clear();
        for (var item : domain.getItems())
            entity.items.add(
                new PurchaseItemJpaValue(
                    item.getTicketTypeId(),
                    item instanceof AssignedSeatingCheckoutItem assigned ? assigned.getSeatId() : null,
                    item.getPrice()
                )
            );
    }

    public CheckoutSession domain(CheckoutJpaEntity entity) {
        List<CheckoutItem> items = entity.items
            .stream()
            .map(item ->
                item.seatId == null
                    ? (CheckoutItem) new GeneralAdmissionCheckoutItem(item.ticketTypeId, item.price())
                    : new AssignedSeatingCheckoutItem(item.ticketTypeId, item.seatId, item.price())
            )
            .toList();
        return new CheckoutSession(
            new CheckoutSessionId(entity.id),
            CheckoutSessionStatus.valueOf(entity.status),
            entity.ownerId,
            entity.eventId,
            InventoryMode.valueOf(entity.mode),
            new CheckoutItemCollection(items),
            entity.promotionId,
            Money.of(entity.discount, Currency.getInstance(entity.discountCurrency)),
            Money.of(entity.total, Currency.getInstance(entity.totalCurrency)),
            entity.paymentId
        );
    }

    public void update(Order domain, OrderJpaEntity entity) {
        entity.id = domain.getId();
        entity.eventId = domain.getEventId();
        entity.ownerId = domain.getOwnerId();
        entity.status = domain.getStatus().name();
        entity.mode = domain.getInventoryMode().name();
        entity.promotionId = domain.getPromotionId();
        entity.discount = domain.getDiscountAmount().amount();
        entity.discountCurrency = domain.getDiscountAmount().currency().getCurrencyCode();
        entity.total = domain.getTotalPrice().amount();
        entity.totalCurrency = domain.getTotalPrice().currency().getCurrencyCode();
        entity.paymentId = domain.getPaymentId();
        entity.reason = domain.getCancellationReason() == null ? null : domain.getCancellationReason().name();
        entity.refundId = domain.getRefundId();
        entity.items.clear();
        for (var item : domain.getItems())
            entity.items.add(
                new PurchaseItemJpaValue(
                    item.getTicketTypeId(),
                    item instanceof AssignedSeatingOrderItem assigned ? assigned.getSeatId() : null,
                    item.getPrice()
                )
            );
    }

    public Order domain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.items
            .stream()
            .map(item ->
                item.seatId == null
                    ? (OrderItem) new GeneralAdmissionOrderItem(item.ticketTypeId, item.price())
                    : new AssignedSeatingOrderItem(item.ticketTypeId, item.seatId, item.price())
            )
            .toList();
        return new Order(
            new OrderId(entity.id),
            OrderStatus.valueOf(entity.status),
            entity.ownerId,
            entity.eventId,
            InventoryMode.valueOf(entity.mode),
            items,
            entity.promotionId,
            Money.of(entity.discount, Currency.getInstance(entity.discountCurrency)),
            Money.of(entity.total, Currency.getInstance(entity.totalCurrency)),
            entity.paymentId,
            entity.reason == null ? null : CancellationReason.valueOf(entity.reason),
            entity.refundId
        );
    }

    public void update(CheckoutProcess domain, CheckoutProcessJpaEntity entity) {
        entity.id = domain.id();
        entity.eventId = domain.eventId();
        entity.ownerId = domain.ownerId();
        entity.fingerprint = domain.fingerprint();
        entity.deadline = domain.deadline();
        entity.paymentId = domain.paymentId();
        entity.refundState = domain.refundState();
        entity.refundId = domain.refundId();
        entity.lines.clear();
        domain.lines().forEach(line -> entity.lines.add(new CheckoutLineJpaValue(line)));
        entity.holds.clear();
        domain.holds().forEach(hold -> entity.holds.add(new CheckoutHoldJpaValue(hold)));
    }

    public CheckoutProcess domain(CheckoutProcessJpaEntity entity) {
        return new CheckoutProcess(
            entity.id,
            entity.ownerId,
            entity.eventId,
            entity.fingerprint,
            entity.deadline,
            entity.lines.stream().map(CheckoutLineJpaValue::domain).toList(),
            entity.holds.stream().map(CheckoutHoldJpaValue::domain).toList(),
            entity.paymentId,
            entity.refundState,
            entity.refundId
        );
    }
}
