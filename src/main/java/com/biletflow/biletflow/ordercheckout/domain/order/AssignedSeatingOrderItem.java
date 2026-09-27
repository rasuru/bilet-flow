package com.biletflow.biletflow.ordercheckout.domain.order;

import java.util.Objects;
import java.util.UUID;
import com.biletflow.biletflow.common.domain.Money;

public final class AssignedSeatingOrderItem implements OrderItemMode {
    private final UUID ticketTypeId;
    private final UUID seatId;
    private final Money price;
    private boolean used;

    public AssignedSeatingOrderItem(UUID ticketTypeId, UUID seatId, Money price) {
        this.ticketTypeId = Objects.requireNonNull(
            ticketTypeId,
            "TicketTypeId can not be null!"
        );
        this.seatId = Objects.requireNonNull(
            seatId,
            "SeatId can not be null!"
        );
        this.price = Objects.requireNonNull(
            price,
            "Price can not be null!"
        );
        this.used = false;
    }

    public AssignedSeatingOrderItem createNew(
        UUID ticketTypeId,
        UUID seatId,
        Money price
    ) {
        return new AssignedSeatingOrderItem(ticketTypeId, seatId, price);
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

    public UUID getSeatId() {
        return seatId;
    }

    public Money getPrice() {
        return price;
    }

    public boolean isUsed() {
        return used;
    }
}
