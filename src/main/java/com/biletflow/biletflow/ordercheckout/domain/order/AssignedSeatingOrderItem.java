package com.biletflow.biletflow.ordercheckout.domain.order;

import java.util.Objects;
import java.util.UUID;
import com.biletflow.biletflow.common.domain.Money;

public final class AssignedSeatingOrderItem implements OrderItem {
    private final UUID ticketTypeId;
    private final UUID seatId;
    private final Money price;

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
    }

    public static AssignedSeatingOrderItem createNew(
        UUID ticketTypeId,
        UUID seatId,
        Money price
    ) {
        return new AssignedSeatingOrderItem(ticketTypeId, seatId, price);
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
}
