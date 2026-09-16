package com.biletflow.biletflow.ordercheckout.domain.checkout;

import java.util.Objects;
import java.util.UUID;

public class GeneralAdmissonCheckoutItem() {
    private final UUID ticketTypeId;
    private final double price;

    // Constructor
    public GeneralAdmissonCheckoutItem(UUID ticketTypeId, double price) {
        this.ticketTypeId = Objects.requireNonNull(ticketTypeId, "TicketTypeId can not be null!");
        this.price = Objects.requireNonNull(price, "Price can not be null!");
    }

    public GeneralAdmissonCheckoutItem createNew(UUID ticketTypeId, double price) {
        Objects.requireNonNull(ticketTypeId, "TicketTypeId can not be null!");
        Objects.requireNonNull(ticketTypeId, "TicketTypeId can not be null!");
        Objects.requireNonNull(price, "Price can not be null!");

        return new GeneralAdmissonCheckoutItem(ticketTypeId, price);
    }

    // Getters
    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public double getPrice() {
        return price;
    }
}
