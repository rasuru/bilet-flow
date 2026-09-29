package com.biletflow.biletflow.ordercheckout.domain.order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSession;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionStatus;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutItem;
import com.biletflow.biletflow.ordercheckout.domain.checkout.AssignedSeatingCheckoutItem;
import com.biletflow.biletflow.ordercheckout.domain.checkout.GeneralAdmissionCheckoutItem;
import com.biletflow.biletflow.common.domain.Money;

public class Order {
    private final OrderId orderId;

    private OrderStatus orderStatus;

    private final long ownerId;
    private final UUID eventId;

    private final InventoryMode inventoryMode;
    private final List<OrderItem> items;

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
        List<OrderItem> items,
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
        this.items = List.copyOf(items);
        this.promotionId = promotionId;
        this.discountAmount = discountAmount;
        this.totalPrice = totalPrice;
        this.finalPrice = finalPrice;
        this.paymentId = paymentId;

        this.orderStatus = OrderStatus.COMPLETED;
    }

    public Order(
        OrderId orderId,
        OrderStatus orderStatus,
        long ownerId,
        UUID eventId,
        InventoryMode inventoryMode,
        List<OrderItem> items,
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
        this.inventoryMode = inventoryMode;
        this.items = List.copyOf(items);
        this.promotionId = promotionId;
        this.discountAmount = discountAmount;
        this.totalPrice = totalPrice;
        this.finalPrice = finalPrice;
        this.paymentId = paymentId;
        this.cancellationReason = cancellationReason;
        this.refundId = refundId;
    }

    public static Order createFromCheckout(CheckoutSession checkoutSession) {
        Objects.requireNonNull(checkoutSession, "Checkout Session can not be null");

        if (checkoutSession.getStatus() != CheckoutSessionStatus.COMPLETED) {
            throw new IllegalStateException("Checkout must be completed!");
        }

        List<OrderItem> orderItems = checkoutSession.getItems()
            .stream()
            .map(Order::convertToOrderItem)
            .toList();

        return new Order(
            checkoutSession.getOwnerId(),
            checkoutSession.getEventId(),
            checkoutSession.getInventoryMode(),
            orderItems,
            checkoutSession.getPromotionId(),
            checkoutSession.getDiscountAmount(),
            checkoutSession.getTotalPrice(),
            checkoutSession.getFinalPrice(),
            checkoutSession.getPaymentId()
        );
    }

    private static OrderItem convertToOrderItem(CheckoutItem item) {
        if (item instanceof AssignedSeatingCheckoutItem assigned) {
            return new AssignedSeatingOrderItem(
                assigned.getTicketTypeId(),
                assigned.getSeatId(),
                assigned.getPrice()
            );
        }

        if (item instanceof GeneralAdmissionCheckoutItem general) {
            return new GeneralAdmissionOrderItem(
                general.getTicketTypeId(),
                general.getPrice()
            );
        }

        throw new IllegalArgumentException("Unsupported checkout item type");
    }

    public void cancelOrderByCustomer() {
        if (orderStatus != OrderStatus.COMPLETED) {
            throw new IllegalStateException("Order can not be cancelled!");
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

    public void markRefunded(UUID refundId) {
        Objects.requireNonNull(refundId, "RefundId cannot be null");

        if (orderStatus != OrderStatus.CANCELLED) {
            throw new IllegalStateException("Only cancelled orders can be refunded!");
        }

        this.refundId = refundId;
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

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
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
