package com.biletflow.biletflow.eventmanagement.domain;

import java.time.Instant;
import java.util.Objects;

public class TicketType {

    private final TicketTypeId id;
    private String name;
    private String description;
    private TicketPricing pricing;
    private int totalQuantity;
    private SalesWindow salesWindow;
    private int maxPerOrder;
    private boolean hidden; // the organizer might want to pause sales

    // Package-private constructor: Should be instantiated via Event aggregate domain methods
    TicketType(
        TicketTypeId id,
        String name,
        String description,
        TicketPricing pricing,
        int totalQuantity,
        SalesWindow salesWindow,
        int maxPerOrder
    ) {
        this.id = Objects.requireNonNull(id, "Id cannot be null");
        this.setName(name);
        this.setDescription(description);
        this.pricing = Objects.requireNonNull(pricing, "Pricing cannot be null");
        this.setTotalQuantity(totalQuantity);
        this.salesWindow = Objects.requireNonNull(salesWindow, "Sales window cannot be null");
        this.setMaxPerOrder(maxPerOrder);
        this.hidden = false;
    }

    public static TicketType create(
        String name,
        String description,
        TicketPricing pricing,
        int totalQuantity,
        SalesWindow salesWindow,
        int maxPerOrder
    ) {
        return new TicketType(TicketTypeId.generate(), name, description, pricing, totalQuantity, salesWindow, maxPerOrder);
    }

    // --- Business Invariants & Behaviors ---

    public boolean isPurchasableAt(Instant now) {
        return !hidden && salesWindow.isOpenAt(now);
    }

    public void hide() {
        this.hidden = true;
    }

    public void reveal() {
        this.hidden = false;
    }

    public void updateDetails(String name, String description, SalesWindow salesWindow) {
        setName(name);
        setDescription(description);
        this.salesWindow = Objects.requireNonNull(salesWindow, "Sales window cannot be null");
    }

    public void updateQuantity(int newQuantity) {
        setTotalQuantity(newQuantity);
    }

    // --- Private Invariant Guards ---

    private void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ticket type name cannot be empty");
        }
        this.name = name.trim();
    }

    private void setDescription(String description) {
        this.description = description != null ? description.trim() : "";
    }

    private void setTotalQuantity(int totalQuantity) {
        if (totalQuantity <= 0) {
            throw new IllegalArgumentException("Total quantity must be greater than zero");
        }
        this.totalQuantity = totalQuantity;
    }

    private void setMaxPerOrder(int maxPerOrder) {
        if (maxPerOrder <= 0) {
            throw new IllegalArgumentException("Max per order must be greater than zero");
        }
        this.maxPerOrder = maxPerOrder;
    }

    // --- Read-Only Getters ---

    public TicketTypeId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public TicketPricing getPricing() {
        return pricing;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public SalesWindow getSalesWindow() {
        return salesWindow;
    }

    public int getMaxPerOrder() {
        return maxPerOrder;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isFree() {
        return pricing instanceof TicketPricing.Free;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TicketType that = (TicketType) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
