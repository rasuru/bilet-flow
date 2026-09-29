package com.biletflow.biletflow.ordercheckout.domain.checkout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;

public class CheckoutSession {
    private final CheckoutSessionId id;
    private CheckoutSessionStatus status;

    private final long ownerId;
    private final UUID eventId;

    private final InventoryMode inventoryMode;
    private final CheckoutItemCollection items;

    private UUID promotionId;
    private Money discountAmount;

    private Money totalPrice;  // Total price before discount
    private Money finalPrice;  // Final price after discount

    private UUID paymentId;

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

        this.discountAmount = Money.zeroKzt();
        this.totalPrice = Money.zeroKzt();
        this.finalPrice = Money.zeroKzt();
    }

    public CheckoutSession(
        CheckoutSessionId id,
        CheckoutSessionStatus status,
        long ownerId,
        UUID eventId,
        InventoryMode inventoryMode,
        CheckoutItemCollection items,
        UUID promotionId,
        Money discountAmount,
        Money totalPrice,
        Money finalPrice,
        UUID paymentId
    ) {
        this.id = id;
        this.status = status;
        this.ownerId = ownerId;
        this.eventId = eventId;
        this.inventoryMode = inventoryMode;
        this.items = items;
        this.promotionId = promotionId;
        this.discountAmount = discountAmount;
        this.totalPrice = totalPrice;
        this.finalPrice = finalPrice;
        this.paymentId = paymentId;
    }

    public void cancel() {
        if (status != CheckoutSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only in-progress sessions can be cancelled!");
        }

        this.status = CheckoutSessionStatus.CANCELLED;
    }

    public void complete() {
        if (status != CheckoutSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only in-progress sessions can be completed!");
        }

        if (items.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot complete a checkout session with no items!");
        }

        this.status = CheckoutSessionStatus.COMPLETED;
    }

    public void addItem(CheckoutItem item) {
        ensureInProgress();
        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null!");
        }

        if (inventoryMode == InventoryMode.GENERAL_ADMISSION
                && item instanceof AssignedSeatingCheckoutItem) {
            throw new IllegalArgumentException(
                "Item inventory mode does not match checkout session inventory mode!"
            );
        }

        if (inventoryMode == InventoryMode.RESERVED_SEATING
                && item instanceof GeneralAdmissionCheckoutItem) {
            throw new IllegalArgumentException(
                "Item inventory mode does not match checkout session inventory mode!"
            );
        }

        items.addItem(item);
        totalPrice = totalPrice.add(item.getPrice());
        recalculateFinalPrice();
    }

    public void removeItem(CheckoutItem item) {
        ensureInProgress();
        items.removeItem(item);
        totalPrice = totalPrice.subtract(item.getPrice());
        recalculateFinalPrice();
    }

    public void clearItems() {
        ensureInProgress();
        items.clear();
        totalPrice = Money.zeroKzt();
        recalculateFinalPrice();
    }

    private void recalculateFinalPrice() {
        finalPrice = totalPrice.subtract(discountAmount);
    }

    public void applyPromotion(UUID promotionId, Money discountAmount) {
        ensureInProgress();
        Objects.requireNonNull(promotionId, "PromotionId cannot be null!");
        Objects.requireNonNull(discountAmount, "Discount amount cannot be null!");

        if (discountAmount.isGreaterThan(totalPrice)) {
            throw new IllegalArgumentException(
                "Discount cannot exceed total price!"
            );
        }

        this.promotionId = promotionId;
        this.discountAmount = discountAmount;
        recalculateFinalPrice();
    }

    public void removePromotion() {
        ensureInProgress();
        this.promotionId = null;
        this.discountAmount = Money.zeroKzt();
        recalculateFinalPrice();
    }

    public void setPaymentId(UUID paymentId) {
        ensureInProgress();
        Objects.requireNonNull(paymentId, "PaymentId cannot be null!");
        this.paymentId = paymentId;
    }

    private void ensureInProgress() {
        if (status != CheckoutSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                "Checkout session is no longer mutable!"
            );
        }
    }

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

    public List<CheckoutItem> getItems() {
        return items.getItems();
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
}
