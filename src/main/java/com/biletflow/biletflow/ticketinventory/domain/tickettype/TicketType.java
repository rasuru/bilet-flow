package com.biletflow.biletflow.ticketinventory.domain.tickettype;

import com.biletflow.biletflow.common.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class TicketType {

    private final TicketTypeId id;
    private final UUID eventId;
    private final String priceCategory;

    private String name;
    private String description;
    private Money price;
    private int maxPerOrder;
    private SalesWindow salesWindow;
    private TicketTypeStatus status;

    public TicketType(
        TicketTypeId id,
        UUID eventId,
        String name,
        String description,
        Money price,
        int maxPerOrder,
        SalesWindow salesWindow,
        TicketTypeStatus status,
        String priceCategory
    ) {
        this.id = Objects.requireNonNull(id, "TicketTypeId cannot be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.name = validateName(name);
        this.description = Objects.requireNonNull(description, "description cannot be null");
        this.price = Objects.requireNonNull(price, "price cannot be null");
        this.maxPerOrder = validateMaxPerOrder(maxPerOrder);
        this.salesWindow = Objects.requireNonNull(salesWindow, "salesWindow cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.priceCategory = normalizePriceCategory(priceCategory);
    }

    public static TicketType createGeneralAdmission(
        UUID eventId,
        String name,
        String description,
        Money price,
        int maxPerOrder,
        SalesWindow salesWindow
    ) {
        return new TicketType(
            TicketTypeId.generate(),
            eventId,
            name,
            description,
            price,
            maxPerOrder,
            salesWindow,
            TicketTypeStatus.ACTIVE,
            null
        );
    }

    public static TicketType createAssignedSeating(
        UUID eventId,
        String name,
        String description,
        Money price,
        int maxPerOrder,
        SalesWindow salesWindow,
        String priceCategory
    ) {
        String normalizedCategory = normalizePriceCategory(priceCategory);

        if (normalizedCategory == null) {
            throw new IllegalArgumentException("Assigned-seating ticket type requires a price category");
        }

        return new TicketType(
            TicketTypeId.generate(),
            eventId,
            name,
            description,
            price,
            maxPerOrder,
            salesWindow,
            TicketTypeStatus.ACTIVE,
            normalizedCategory
        );
    }

    public void updateDetails(String newName, String newDescription, Money newPrice, SalesWindow newSalesWindow, int newMaxPerOrder) {
        ensureNotClosed();

        this.name = validateName(newName);
        this.description = Objects.requireNonNull(newDescription, "newDescription cannot be null");
        this.price = Objects.requireNonNull(newPrice, "newPrice cannot be null");
        this.salesWindow = Objects.requireNonNull(newSalesWindow, "newSalesWindow cannot be null");
        this.maxPerOrder = validateMaxPerOrder(newMaxPerOrder);
    }

    public void pause() {
        ensureNotClosed();
        status = TicketTypeStatus.PAUSED;
    }

    public void activate() {
        ensureNotClosed();
        status = TicketTypeStatus.ACTIVE;
    }

    public void hide() {
        ensureNotClosed();
        status = TicketTypeStatus.HIDDEN;
    }

    public void unhide() {
        ensureNotClosed();

        if (status != TicketTypeStatus.HIDDEN) {
            throw new IllegalStateException("Ticket type is not hidden");
        }

        status = TicketTypeStatus.ACTIVE;
    }

    public void close() {
        status = TicketTypeStatus.CLOSED;
    }

    public boolean isPurchasableAt(Instant now) {
        return status == TicketTypeStatus.ACTIVE && salesWindow.isOpenAt(now);
    }

    private static String validateName(String name) {
        Objects.requireNonNull(name, "name cannot be null");

        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("TicketType name cannot be blank");
        }

        return trimmed;
    }

    private static int validateMaxPerOrder(int maxPerOrder) {
        if (maxPerOrder <= 0) {
            throw new IllegalArgumentException("Maximum tickets per order must be greater than 0");
        }

        return maxPerOrder;
    }

    private static String normalizePriceCategory(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void ensureNotClosed() {
        if (status == TicketTypeStatus.CLOSED) {
            throw new IllegalStateException("Cannot modify a closed TicketType");
        }
    }

    public TicketTypeId getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Money getPrice() {
        return price;
    }

    public int getMaxPerOrder() {
        return maxPerOrder;
    }

    public SalesWindow getSalesWindow() {
        return salesWindow;
    }

    public TicketTypeStatus getStatus() {
        return status;
    }

    public String getPriceCategory() {
        return priceCategory;
    }

    public boolean isAssignedSeatingType() {
        return priceCategory != null;
    }

    public boolean isFree() {
        return price.isZero();
    }
}
