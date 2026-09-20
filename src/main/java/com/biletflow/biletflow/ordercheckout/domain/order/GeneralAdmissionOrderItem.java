package com.biletflow.biletflow.ordercheckout.domain.order;

import java.util.Objects;
import java.util.UUID;

public final class GeneralAdmissionOrderItem implements OrderItemMode {
    private final UUID ticketTypeId;
    private final double price;
    private boolean used;

    public GeneralAdmissionOrderItem(UUID ticketTypeId, double price) {
        this.ticketTypeId = Objects.requireNonNull(ticketTypeId, "TicketTypeId should not be null!");
        if (price < 0) {
            throw new IllegalArgumentException("Price must be a positive number!");
        }

        this.price = price;
        this.used = false;
    }

    public GeneralAdmissionOrderItem createNew(UUID ticketTypeId, double price) {
        return new GeneralAdmissionOrderItem(ticketTypeId, price);
    }

    public void markAsUsed() {
        if (used) {
            throw new IllegalStateException("Ticket is already used!");
        }

        used = true;
    }

    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public double getPrice() {
        return price;
    }

    public boolean isUsed() {
        return used;
    }
}
