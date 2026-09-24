package com.biletflow.biletflow.ordercheckout.domain.checkout;

import com.biletflow.biletflow.common.domain.*;
import java.util.Objects;
import java.util.UUID;

public final class AssignedSeatingCheckoutItem implements CheckoutItemMode {
    private final UUID ticketTypeId;
    private final UUID seatId;
    private final Money price;

    // Constructor
    public AssignedSeatingCheckoutItem(UUID ticketTypeId, UUID seatId, Money price) {
        this.ticketTypeId = Objects.requireNonNull(ticketTypeId, "TicketTypeId can not be null!");
        this.seatId = Objects.requireNonNull(seatId, "SeatId can not be null!");

        if (price < 0) {
            throw new IllegalArgumentException("Price should be a positive number!");
        }

        this.price = price;
    }

    public static AssignedSeatingCheckoutItem createNew(UUID ticketTypeId, UUID seatId, Money price) {
        return new AssignedSeatingCheckoutItem(ticketTypeId, seatId, price);
    }

    // Getters
    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public Money getPrice() {
        return price;
    }
}
