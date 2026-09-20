package com.biletflow.biletflow.ordercheckout.domain.checkout;

import com.biletflow.biletflow.ordercheckout.domain.common.*;
import com.biletflow.biletflow.ordercheckout.domain.checkout.enums.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CheckoutSession {
    private final CheckoutSessionId id;
    private CheckoutSessionStatus status;

    private final long ownerId;
    private final UUID eventId;

    private final InventoryMode inventoryMode;
    private final CheckoutItemCollection items;

    private UUID promotionId;
    private PromotionStatus promotionStatus;
    private double discountAmount;

    private double totalPrice;  // Total price before discount
    private double finalPrice;  // Final price after discount

    private UUID paymentId;

    // Constructor
    public CheckoutSession(CheckoutSessionId id, long ownerId, UUID eventId, InventoryMode inventoryMode) {
        Objects.requireNonNull(id, "CheckoutSessionId can not be null!");
        if (ownerId <= 0) {
            throw new IllegalArgumentException("OwnerId must be a positive number!");
        }
        Objects.requireNonNull(eventId, "EventId can not be null!");
        Objects.requireNonNull(inventoryMode, "InventoryMode can not be null!");

        this.id = id;
        this.status = CheckoutSessionStatus.IN_PROGRESS;
        this.ownerId = ownerId;
        this.eventId = eventId;
        this.inventoryMode = inventoryMode;
        this.items = new CheckoutItemCollection();
        this.promotionStatus = PromotionStatus.PENDING;
    }

    // Cancel the checkout session
    public void cancel() {
        if (status != CheckoutSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only in-progress sessions can be cancelled!");
        }

        this.status = CheckoutSessionStatus.CANCELLED;
    }

    // Complete the checkout session
    public void complete() {
        if (status != CheckoutSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only in-progress sessions can be completed!");
        }

        if (items.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot complete a checkout session with no items!");
        }

        this.status = CheckoutSessionStatus.COMPLETED;
    }

    // -- Collection methods
    // Add item to the collection
    public void addItem(CheckoutItemMode item) {
        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null!");
        }

        if (inventoryMode == InventoryMode.GENERAL_ADMISSION && item instanceof AssignedSeatingCheckoutItem) {
            throw new IllegalArgumentException("Item inventory mode does not match checkout session inventory mode!");
        }

        if (inventoryMode == InventoryMode.RESERVED_SEATING && item instanceof GeneralAdmissionCheckoutItem) {
            throw new IllegalArgumentException("Item inventory mode does not match checkout session inventory mode!");
        }

        items.addItem(item);
        totalPrice += item.getPrice();
        recalculateFinalPrice();
    }

    // Remove item from the collection
    public void removeItem(CheckoutItemMode item) {
        items.removeItem(item);
        totalPrice -= item.getPrice();
        recalculateFinalPrice();
    }

    // Clear the collection
    public void clearItems() {
        items.clear();
        totalPrice = 0;
        recalculateFinalPrice();
    }

    // Recalculate final price after any changes
    private void recalculateFinalPrice() {
        finalPrice = totalPrice - discountAmount;
    }

    // -- Promotion methods
    // Apply promotion
    public void applyPromotion(UUID promotionId, double discountAmount) {
        Objects.requireNonNull(promotionId, "PromotionId cannot be null!");
        if (discountAmount < 0) {
            throw new IllegalArgumentException("Discount amount cannot be negative!");
        }

        this.promotionStatus = PromotionStatus.APPLIED;
        this.promotionId = promotionId;
        this.discountAmount = discountAmount;
        recalculateFinalPrice();
    }

    // Remove promotion
    public void removePromotion() {
        this.promotionId = null;
        this.discountAmount = 0;
        recalculateFinalPrice();
    }

    // Payment methods
    public void setPaymentId(UUID paymentId) {
        Objects.requireNonNull(paymentId, "PaymentId cannot be null!");
        this.paymentId = paymentId;
    }

    // Getters
    public CheckoutSessionId getId() {
        return id;
    }

    public CheckoutSessionStatus getStatus() {
        return status;
    }

    public InventoryMode getInventoryMode() {
        return inventoryMode;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public List<CheckoutItemMode> getItems() {
        return items.getItems();
    }

    public UUID getPromotionId() {
        return promotionId;
    }

    public double getDiscountAmount() {
        return discountAmount;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public double getFinalPrice() {
        return finalPrice;
    }

    public UUID getPaymentId() {
        return paymentId;
    }
}
