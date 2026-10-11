
package com.biletflow.biletflow.ordercheckout.domain.checkout;

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
    private Money totalPrice;

    private UUID paymentId;

    public CheckoutSession(
        CheckoutSessionId id,
        long ownerId,
        UUID eventId,
        InventoryMode inventoryMode
    ) {
        this.id = Objects.requireNonNull(id, "CheckoutSessionId cannot be null!");

        if (ownerId <= 0) {
            throw new IllegalArgumentException("OwnerId must be positive!");
        }

        this.ownerId = ownerId;
        this.eventId = Objects.requireNonNull(eventId, "EventId cannot be null!");
        this.inventoryMode = Objects.requireNonNull(
            inventoryMode, "InventoryMode cannot be null!"
        );

        this.status = CheckoutSessionStatus.IN_PROGRESS;
        this.items = new CheckoutItemCollection();
        this.discountAmount = Money.zeroKzt();
        this.totalPrice = Money.zeroKzt();
    }

    // Reconstitution constructor
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
        UUID paymentId
    ) {
        this.id = Objects.requireNonNull(id, "CheckoutSessionId cannot be null!");
        this.status = Objects.requireNonNull(status, "Status cannot be null!");

        if (ownerId <= 0) {
            throw new IllegalArgumentException("OwnerId must be positive!");
        }

        this.ownerId = ownerId;
        this.eventId = Objects.requireNonNull(eventId, "EventId cannot be null!");
        this.inventoryMode = Objects.requireNonNull(
            inventoryMode, "InventoryMode cannot be null!"
        );
        this.items = Objects.requireNonNull(items, "Items cannot be null!");
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

    public void cancel() {
        ensureInProgress();
        status = CheckoutSessionStatus.CANCELLED;
    }

    public void completeFreeCheckout() {
        ensureInProgress();
        ensureHasItems();

        if (!getFinalPrice().equals(Money.zeroKzt())) {
            throw new IllegalStateException("Checkout is not free!");
        }

        status = CheckoutSessionStatus.COMPLETED;
    }

    public void awaitPayment() {
        ensureInProgress();
        ensureHasItems();

        if (getFinalPrice().equals(Money.zeroKzt())) {
            throw new IllegalStateException(
                "Free checkout does not require payment!"
            );
        }

        status = CheckoutSessionStatus.AWAITING_PAYMENT;
    }

    /**
     * Call only after the application layer has verified successful payment.
     */
    public void confirmPayment(UUID paymentId) {
        Objects.requireNonNull(paymentId, "PaymentId cannot be null!");

        if (status != CheckoutSessionStatus.AWAITING_PAYMENT) {
            throw new IllegalStateException(
                "Checkout is not awaiting payment!"
            );
        }

        this.paymentId = paymentId;
        status = CheckoutSessionStatus.COMPLETED;
    }

    public void addItem(CheckoutItem item) {
        ensureInProgress();
        Objects.requireNonNull(item, "Item cannot be null!");

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
    }

    public void removeAssignedSeat(UUID seatId) {
        ensureInProgress();
        Objects.requireNonNull(seatId, "SeatId cannot be null!");

        if (inventoryMode != InventoryMode.RESERVED_SEATING) {
            throw new IllegalStateException(
                "Cannot remove an assigned seat from a general admission checkout!"
            );
        }

        AssignedSeatingCheckoutItem item = items.getItems().stream()
            .filter(AssignedSeatingCheckoutItem.class::isInstance)
            .map(AssignedSeatingCheckoutItem.class::cast)
            .filter(i -> i.getSeatId().equals(seatId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Assigned seat was not found in checkout!"
            ));

        removeItemInternal(item);
    }

    /**
     * Removes one item matching the ticket type.
     * If duplicate ticket-type entries are allowed, this removes only the first.
     */
    public void removeGeneralAdmissionItem(UUID ticketTypeId) {
        ensureInProgress();
        Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null!");

        if (inventoryMode != InventoryMode.GENERAL_ADMISSION) {
            throw new IllegalStateException(
                "Cannot remove a general admission item from a reserved seating checkout!"
            );
        }

        GeneralAdmissionCheckoutItem item = items.getItems().stream()
            .filter(GeneralAdmissionCheckoutItem.class::isInstance)
            .map(GeneralAdmissionCheckoutItem.class::cast)
            .filter(i -> i.getTicketTypeId().equals(ticketTypeId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "General admission item was not found in checkout!"
            ));

        removeItemInternal(item);
    }

    private void removeItemInternal(CheckoutItem item) {
        items.removeItem(item);
        totalPrice = totalPrice.subtract(item.getPrice());

        if (discountAmount.isGreaterThan(totalPrice)) {
            removePromotion();
        }
    }

    public void clearItems() {
        ensureInProgress();
        items.clear();
        totalPrice = Money.zeroKzt();
        removePromotion();
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
    }

    public void removePromotion() {
        ensureInProgress();
        promotionId = null;
        discountAmount = Money.zeroKzt();
    }

    private void ensureInProgress() {
        if (status != CheckoutSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                "Checkout session is no longer mutable!"
            );
        }
    }

    private void ensureHasItems() {
        if (items.getItems().isEmpty()) {
            throw new IllegalStateException(
                "Checkout cannot proceed without items!"
            );
        }
    }

    private void validatePricing() {
        if (discountAmount.isGreaterThan(totalPrice)) {
            throw new IllegalArgumentException(
                "Discount cannot exceed total price!"
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
        return totalPrice.subtract(discountAmount);
    }

    public UUID getPaymentId() {
        return paymentId;
    }
}
