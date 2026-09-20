package com.biletflow.biletflow.ordercheckout.domain.checkout;

import java.util.Objects;
import java.util.UUID;

public final class GeneralAdmissionCheckoutItem implements CheckoutItemMode {
    private final UUID ticketTypeId;
    private final double price;

    // Constructor
    public GeneralAdmissionCheckoutItem(UUID ticketTypeId, double price) {
        this.ticketTypeId = Objects.requireNonNull(ticketTypeId, "TicketTypeId can not be null!");

        if (price < 0) {
            throw new IllegalArgumentException("Price must be a positive number!");
        }

        this.price = price;
    }

    public static GeneralAdmissionCheckoutItem createNew(UUID ticketTypeId, double price) {
        return new GeneralAdmissionCheckoutItem(ticketTypeId, price);
    }

    // Getters
    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public double getPrice() {
        return price;
    }
}
