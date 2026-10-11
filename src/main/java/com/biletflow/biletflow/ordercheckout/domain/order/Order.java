
package com.biletflow.biletflow.ordercheckout.domain.order;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import com.biletflow.biletflow.ordercheckout.domain.checkout.AssignedSeatingCheckoutItem;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutItem;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSession;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionStatus;
import com.biletflow.biletflow.ordercheckout.domain.checkout.GeneralAdmissionCheckoutItem;

public class Order {
    private final OrderId orderId;
    private OrderStatus orderStatus;

    private final long ownerId;
    private final UUID eventId;

    private final InventoryMode inventoryMode;
    private final List<OrderItem> items;

    private final UUID promotionId;
    private final Money discountAmount;
    private final Money totalPrice;

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
        UUID paymentId
    ) {
        if (ownerId <= 0) {
            throw new IllegalArgumentException("OwnerId must be positive!");
        }

        this.orderId = OrderId.generate();
        this.orderStatus = OrderStatus.COMPLETED;
        this.ownerId = ownerId;
        this.eventId = Objects.requireNonNull(eventId, "EventId cannot be null!");
        this.inventoryMode = Objects.requireNonNull(
            inventoryMode, "InventoryMode cannot be null!"
        );
        this.items = List.copyOf(items);
        this.promotionId = promotionId;
        this.discountAmount = Objects.requireNonNull(
            discountAmount, "Discount amount cannot be null!"
        );
        this.totalPrice = Objects.requireNonNull(
            totalPrice, "Total price cannot be null!"
        );

        validatePricing();

        this.paymentId = paymentId;
    }

    /**
     * Reconstitution constructor for restoring an Order from persistence.
     */
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
        UUID paymentId,
        CancellationReason cancellationReason,
        UUID refundId
    ) {
        this.orderId = Objects.requireNonNull(
            orderId, "OrderId cannot be null!"
        );
        this.orderStatus = Objects.requireNonNull(
            orderStatus, "OrderStatus cannot be null!"
        );

        if (ownerId <= 0) {
            throw new IllegalArgumentException("OwnerId must be positive!");
        }

        this.ownerId = ownerId;
        this.eventId = Objects.requireNonNull(eventId, "EventId cannot be null!");
        this.inventoryMode = Objects.requireNonNull(
            inventoryMode, "InventoryMode cannot be null!"
        );
        this.items = List.copyOf(
            Objects.requireNonNull(items, "Items cannot be null!")
        );
        this.promotionId = promotionId;
        this.discountAmount = Objects.requireNonNull(
            discountAmount, "Discount amount cannot be null!"
        );
        this.totalPrice = Objects.requireNonNull(
            totalPrice, "Total price cannot be null!"
        );

        validatePricing();

        this.paymentId = paymentId;
        this.cancellationReason = cancellationReason;
        this.refundId = refundId;
    }

    public static Order createFromCheckout(CheckoutSession checkoutSession) {
        Objects.requireNonNull(
            checkoutSession, "CheckoutSession cannot be null!"
        );

        if (checkoutSession.getStatus() != CheckoutSessionStatus.COMPLETED) {
            throw new IllegalStateException(
                "Checkout must be completed before creating an order!"
            );
        }

        if (!checkoutSession.getFinalPrice().equals(Money.zeroKzt())
                && checkoutSession.getPaymentId() == null) {
            throw new IllegalStateException(
                "A paid checkout must have a confirmed payment!"
            );
        }

        List<OrderItem> orderItems = checkoutSession.getItems()
            .stream()
            .map(Order::convertToOrderItem)
            .toList();

        if (orderItems.isEmpty()) {
            throw new IllegalStateException(
                "Cannot create an order without items!"
            );
        }

        return new Order(
            checkoutSession.getOwnerId(),
            checkoutSession.getEventId(),
            checkoutSession.getInventoryMode(),
            orderItems,
            checkoutSession.getPromotionId(),
            checkoutSession.getDiscountAmount(),
            checkoutSession.getTotalPrice(),
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

        throw new IllegalArgumentException(
            "Unsupported checkout item type: " + item.getClass().getName()
        );
    }

    private void validatePricing() {
        if (discountAmount.isGreaterThan(totalPrice)) {
            throw new IllegalArgumentException(
                "Discount cannot exceed total price!"
            );
        }
    }

    public void cancelOrderByCustomer() {
        ensureCompleted();

        orderStatus = OrderStatus.CANCELLED;
        cancellationReason = CancellationReason.CUSTOMER_REQUEST;
    }

    public void cancelOrderByManager() {
        ensureCompleted();

        orderStatus = OrderStatus.CANCELLED;
        cancellationReason = CancellationReason.EVENT_CANCELLED;
    }

    private void ensureCompleted() {
        if (orderStatus != OrderStatus.COMPLETED) {
            throw new IllegalStateException(
                "Only completed orders can be cancelled!"
            );
        }
    }

    /**
     * Call after the Payments & Monetization context confirms a successful refund.
     * Repeated delivery of the same refund ID is idempotent.
     */
    public void markRefunded(UUID refundId) {
        Objects.requireNonNull(refundId, "RefundId cannot be null!");

        if (orderStatus != OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                "Only cancelled orders can be refunded!"
            );
        }

        if (this.refundId != null) {
            if (this.refundId.equals(refundId)) {
                return;
            }

            throw new IllegalStateException(
                "Order already has an associated refund!"
            );
        }

        this.refundId = refundId;
    }

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
        return totalPrice.subtract(discountAmount);
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
