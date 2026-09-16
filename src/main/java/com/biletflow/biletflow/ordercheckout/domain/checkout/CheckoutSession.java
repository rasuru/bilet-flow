package com.biletflow.biletflow.ordercheckout.domain.checkout;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class CheckoutSession {
    private final CheckoutSessionId id;
    private CheckoutSessionStatus status;

    private final long ownerId;
    private final UUID eventId;

    private final CheckoutItemCollection items;

    private String appliedPromotion;
    private double discountAmount;

    private double totalPrice;  // Total price before discount
    private double finalPrice;  // Final price after discount

    private final UUID paymentId;
    private final PaymentStatus paymentStatus;

    // Constructor
    public CheckoutSession(CheckoutSessionId id, long ownerId, UUID eventId) {
        Objects.requireNonNull(id, "CheckoutSessionId can not be null!");
        Objects.requireNonNull(ownerId, "OwnerId can not be null!");
        Objects.requireNonNull(eventId, "EventId can not be null!");

        this.id = id;
        this.status = CheckoutSessionStatus.IN_PROGRESS;
        this.ownerId = ownerId;
        this.eventId = eventId;
        this.items = new CheckoutItemCollection();
    }

    // Cancel the checkout session
    public void cancel() {
        // Implement cancellation logic here
    }

    // Complete the checkout session
    public void complete() {
        // Implement completion logic here

        // Create Order
    }

    // -- Collection methods
    // Add item to the collection
    public void addItem(CheckoutItemMode item) {
        items.addItem(item);
        totalPrice += item.getPrice();
    }

    // Remove item from the collection
    public void removeItem(CheckoutItemMode item) {
        items.removeItem(item);
        totalPrice -= item.getPrice();
    }

    // Clear the collection
    public void clearItems() {
        items.clear();
        totalPrice = 0;
    }

    // -- Promotion methods
    // Apply promotion
    public void applyPromotion(String promotionCode, double discountAmount) {
        Objects.requireNonNull(promotionCode, "Promotion code cannot be null!");
        if (discountAmount < 0) {
            throw new IllegalArgumentException("Discount amount cannot be negative!");
        }

        this.appliedPromotion = promotionCode;
        this.discountAmount = discountAmount;
    }

    // Remove promotion
    public void removePromotion() {
        this.appliedPromotion = null;
        this.discountAmount = 0;
    }

    // Payment methods
    public void setPaymentId(UUID paymentId) {
        Objects.requireNonNull(paymentId, "PaymentId cannot be null!");
        this.paymentId = paymentId;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        Objects.requireNonNull(paymentStatus, "PaymentStatus cannot be null!");
        this.paymentStatus = paymentStatus;
    }

    // Getters
    public CheckoutSessionId getId() {
        return id;
    }

    public CheckoutSessionStatus getStatus() {
        return status;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public CheckoutItemCollection getItems() {
        return items.getItems();
    }
}
