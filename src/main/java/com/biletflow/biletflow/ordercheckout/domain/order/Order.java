package com.biletflow.biletflow.ordercheckout.domain.order;

import com.biletflow.biletflow.ordercheckout.domain.order.enums.*;
import com.biletflow.biletflow.ordercheckout.domain.common.*;
import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import com.biletflow.biletflow.ordercheckout.domain.checkout.enums.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class Order {
    private final OrderId orderId;

    private OrderStatus orderStatus;

    private final long ownerId;
    private final UUID eventId;
    private Instant eventStartTime;
    private Instant eventEndTime;

    private final Clock clock;

    private final InventoryMode inventoryMode;
    private final List<OrderItemMode> items;

    private final UUID promotionId;
    private final Money discountAmount;

    private final Money totalPrice;  // Total price before discount
    private final Money finalPrice;  // Final price after discount

    private final UUID paymentId;

    private CancellationReason cancellationReason;
    private UUID refundId;

    private Order(
        long ownerId,
        UUID eventId,
        InventoryMode inventoryMode,
        List<OrderItemMode> items,
        UUID promotionId,
        Money discountAmount,
        Money totalPrice,
        Money finalPrice,
        UUID paymentId

    ) {
        this.orderId = OrderId.generate();
        this.ownerId = ownerId;
        this.eventId = eventId;
        this.inventoryMode = inventoryMode;
        this.items = new ArrayList<>(items);
        this.promotionId = promotionId;
        this.discountAmount = discountAmount;
        this.totalPrice = totalPrice;
        this.finalPrice = finalPrice;
        this.paymentId = paymentId;

        this.orderStatus = OrderStatus.IN_PROGRESS;
        this.clock = Clock.systemUTC();
    }

    public Order(
        OrderId orderId,
        OrderStatus orderStatus,
        long ownerId,
        UUID eventId,
        Instant eventStartTime,
        Instant eventEndTime,
        InventoryMode inventoryMode,
        List<OrderItemMode> items,
        UUID promotionId,
        Money discountAmount,
        Money totalPrice,
        Money finalPrice,
        UUID paymentId,
        CancellationReason cancellationReason,
        UUID refundId
    ) {
        this.orderId = orderId;
        this.orderStatus = orderStatus;
        this.ownerId = ownerId;
        this.eventId = eventId;
        this.eventStartTime = eventStartTime;
        this.eventEndTime = eventEndTime;
        this.inventoryMode = inventoryMode;
        this.items = new ArrayList<>(items);
        this.promotionId = promotionId;
        this.discountAmount = discountAmount;
        this.totalPrice = totalPrice;
        this.finalPrice = finalPrice;
        this.paymentId = paymentId;
        this.cancellationReason = cancellationReason;
        this.refundId = refundId;
        this.clock = Clock.systemUTC();
    }

    public static Order createFromCheckout(CheckoutSession checkoutSession) {
        Objects.requireNonNull(checkoutSession, "Checkout Session can not be null");

        if (checkoutSession.getStatus() != CheckoutSessionStatus.COMPLETED) {
            throw new IllegalStateException("Checkout must be completed!");
        }

        return new Order(
            checkoutSession.getOwnerId(),
            checkoutSession.getEventId(),
            checkoutSession.getInventoryMode(),
            checkoutSession.getItems()
                .stream()
                .map(OrderItemMode::convertToOrderItem)
                .toList(),
            checkoutSession.getPromotionId(),
            checkoutSession.getDiscountAmount(),
            checkoutSession.getTotalPrice(),
            checkoutSession.getFinalPrice(),
            checkoutSession.getPaymentId()
        );
    }

    public void cancelOrderByCustomer() {
        Instant timeNow = clock.instant();

        if (orderStatus != OrderStatus.COMPLETED) {
            throw new IllegalStateException("Order can not be cancelled!");
        }

        if (timeNow.isAfter(eventStartTime.minus(10, ChronoUnit.MINUTES))) {
            throw new IllegalStateException(
                "Order cannot be cancelled less than 10 minutes before the event!"
            );
        }

        if (items.stream().anyMatch(OrderItemMode::isUsed)) {
            throw new IllegalStateException(
                "Order cannot be cancelled because a ticket has been used!"
            );
        }

        orderStatus = OrderStatus.CANCELLED;
        cancellationReason = CancellationReason.CUSTOMER_REQUEST;
    }

    public void cancelOrderByManager() {
        if (orderStatus != OrderStatus.COMPLETED) {
            throw new IllegalStateException("Order can not be cancelled!");
        }

        orderStatus = OrderStatus.CANCELLED;
        cancellationReason = CancellationReason.EVENT_CANCELLED;
    }


    // Getters
    public UUID getId() {
        return orderId.value();
    }

    public OrderStatus getStatus() {
        return orderStatus;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public List<OrderItemMode> getItems() {
        return items;
    }

    public UUID getPromotionId() {
        return promotionId;
    }

    public Money getDiscountAmount() {
        return discountAmount;
    }

    public Money getTotalPrice() {
        return totalPrice;
    }

    public Money getFinalPrice() {
        return finalPrice;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Instant getEventStartTime() {
        return eventStartTime;
    }

    public Instant getEventEndTime() {
        return eventEndTime;
    }

    public InventoryMode getInventoryMode() {
        return inventoryMode;
    }

    public CancellationReason getCancellationReason() {
        return cancellationReason;
    }

    public UUID getRefundId() {
        return refundId;
    }
}
