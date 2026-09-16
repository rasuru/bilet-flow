package com.biletflow.biletflow.ordercheckout.domain.checkout;

import java.util.Objects;
import java.util.UUID;

public class AssignedSeatingCheckoutItem() {
    private final UUID ticketTypeId;
    private final UUID seatId;
    private final double price;

    // Constructor
    public AssignedSeatingCheckoutItem(UUID ticketTypeId, UUID seatId, double price) {
        this.ticketTypeId = Objects.requireNonNull(ticketTypeId, "TicketTypeId can not be null!");
        this.seatId = Objects.requireNonNull(seatId, "SeatId can not be null!");
        this.price = Objects.requireNonNull(price, "Price can not be null!");
    }

    public AssignedSeatingCheckoutItem createNew(UUID ticketTypeId, UUID seatId, double price) {
        Objects.requireNonNull(ticketTypeId, "TicketTypeId can not be null!");
        Objects.requireNonNull(seatId, "SeatId can not be null!");
        Objects.requireNonNull(price, "Price can not be null!");

        return new AssignedSeatingCheckoutItem(ticketTypeId, seatId, price);
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
}
