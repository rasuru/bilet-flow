package com.biletflow.biletflow.ordercheckout.domain.cart;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Cart {
    private final CartId id;
    private final long userId;
    private final CartItemCollection items;
    private final Instant createdAt;

    public Cart(CartId id, long userId, CartItemCollection items, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "CartId cannot be null");
        this.userId = userId;
        this.items = Objects.requireNonNull(items, "Cart items cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt cannot be null");
    }

    public static Cart createNew(long userId) {
        return new Cart(new CartId(CartId.generate().value()), userId, CartItemCollection.empty(), Instant.now());
    }

    public void addItem(UUID eventId, UUID ticketTypeId, int quantity) {
        CartItem item = CartItem.createNew(eventId, ticketTypeId, quantity);

        items.addItem(item);
    }

    public void removeItem(UUID eventId, UUID ticketTypeId) {
        Objects.requireNonNull(eventId, "EventId cannot be null");
        Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null");
        items.removeItem(eventId, ticketTypeId);
    }

    public void changeItemQuantity(UUID eventId, UUID ticketTypeId, int newQuantity) {
        Objects.requireNonNull(eventId, "EventId cannot be null");
        Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null");
        items.changeItemQuantity(eventId, ticketTypeId, newQuantity);
    }

    public void clearItems() {
        items.clear();
    }

    public CartId getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public CartItemCollection getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
