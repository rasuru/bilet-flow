package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import com.biletflow.biletflow.ordercheckout.domain.order.*;
import java.util.List;
import java.util.UUID;

/** Explicit version-one persistence DTOs avoid polymorphic domain deserialization. */
final class CheckoutSnapshots {

    record Item(UUID ticketTypeId, UUID seatId, Money price) {}

    record Session(
        UUID id,
        CheckoutSessionStatus status,
        long ownerId,
        UUID eventId,
        InventoryMode mode,
        List<Item> items,
        UUID promotionId,
        Money discount,
        Money total,
        UUID paymentId
    ) {
        static Session of(CheckoutSession c) {
            return new Session(
                c.getId().value(),
                c.getStatus(),
                c.getOwnerId(),
                c.getEventId(),
                c.getInventoryMode(),
                c
                    .getItems()
                    .stream()
                    .map(i ->
                        new Item(i.getTicketTypeId(), i instanceof AssignedSeatingCheckoutItem a ? a.getSeatId() : null, i.getPrice())
                    )
                    .toList(),
                c.getPromotionId(),
                c.getDiscountAmount(),
                c.getTotalPrice(),
                c.getPaymentId()
            );
        }

        CheckoutSession domain() {
            List<CheckoutItem> converted = items
                .stream()
                .map(i ->
                    i.seatId() == null
                        ? (CheckoutItem) new GeneralAdmissionCheckoutItem(i.ticketTypeId(), i.price())
                        : new AssignedSeatingCheckoutItem(i.ticketTypeId(), i.seatId(), i.price())
                )
                .toList();
            return new CheckoutSession(
                new CheckoutSessionId(id),
                status,
                ownerId,
                eventId,
                mode,
                new CheckoutItemCollection(converted),
                promotionId,
                discount,
                total,
                paymentId
            );
        }
    }

    record Sale(
        UUID id,
        OrderStatus status,
        long ownerId,
        UUID eventId,
        InventoryMode mode,
        List<Item> items,
        UUID promotionId,
        Money discount,
        Money total,
        UUID paymentId,
        CancellationReason reason,
        UUID refundId
    ) {
        static Sale of(Order o) {
            return new Sale(
                o.getId(),
                o.getStatus(),
                o.getOwnerId(),
                o.getEventId(),
                o.getInventoryMode(),
                o
                    .getItems()
                    .stream()
                    .map(i -> new Item(i.getTicketTypeId(), i instanceof AssignedSeatingOrderItem a ? a.getSeatId() : null, i.getPrice()))
                    .toList(),
                o.getPromotionId(),
                o.getDiscountAmount(),
                o.getTotalPrice(),
                o.getPaymentId(),
                o.getCancellationReason(),
                o.getRefundId()
            );
        }

        Order domain() {
            List<OrderItem> converted = items
                .stream()
                .map(i ->
                    i.seatId() == null
                        ? (OrderItem) new GeneralAdmissionOrderItem(i.ticketTypeId(), i.price())
                        : new AssignedSeatingOrderItem(i.ticketTypeId(), i.seatId(), i.price())
                )
                .toList();
            return new Order(
                new OrderId(id),
                status,
                ownerId,
                eventId,
                mode,
                converted,
                promotionId,
                discount,
                total,
                paymentId,
                reason,
                refundId
            );
        }
    }
}
