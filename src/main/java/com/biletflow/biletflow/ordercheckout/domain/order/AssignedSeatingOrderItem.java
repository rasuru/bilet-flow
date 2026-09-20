package com.biletflow.biletflow.ordercheckout.domain.order;

import java.util.Objects;
import java.util.UUID;

public final class AssignedSeatingOrderItem implements OrderItemMode {
    private final UUID ticketTypeId;
    private final UUID seatId;
    private final double price;
    private boolean used;
    
    // Constructor
    public AssignedSeatingOrderItem(UUID ticketTypeId, UUID seatId, double price) {
        this.ticketTypeId = Objects.requireNonNull(ticketTypeId, "TicketTypeId can not be null!");
        this.seatId = Objects.requireNonNull(seatId, "SeatId can not be null!");

        if (price < 0) {
            throw new IllegalArgumentException("Price should be a positive number!");
        }

        this.price = price;
        this.used = false;
    }

    public AssignedSeatingOrderItem createNew(UUID ticketTypeId, UUID seatId, double price) {
        return new AssignedSeatingOrderItem(ticketTypeId, seatId, price);
    }

    public void markAsUsed() {
        if (used) {
            throw new IllegalStateException("Ticket is already used!");
        }

        used = true;
    }

    // Getters
    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public double getPrice() {
        return price;
    }

    public boolean isUsed() {
        return used;
    }
}
