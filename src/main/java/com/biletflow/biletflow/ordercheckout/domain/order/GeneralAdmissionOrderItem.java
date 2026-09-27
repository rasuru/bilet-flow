package com.biletflow.biletflow.ordercheckout.domain.order;

import java.util.Objects;
import java.util.UUID;
import com.biletflow.biletflow.common.domain.Money;

public final class GeneralAdmissionOrderItem implements OrderItemMode {
    private final UUID ticketTypeId;
    private final Money price;
    private boolean used;

    public GeneralAdmissionOrderItem(UUID ticketTypeId, Money price) {
        this.ticketTypeId = Objects.requireNonNull(
            ticketTypeId,
            "TicketTypeId should not be null!"
        );
        this.price = Objects.requireNonNull(
            price,
            "Price should not be null!"
        );
        this.used = false;
    }

    public GeneralAdmissionOrderItem createNew(UUID ticketTypeId, Money price) {
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

    public Money getPrice() {
        return price;
    }

    public boolean isUsed() {
        return used;
    }
}
