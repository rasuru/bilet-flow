package com.biletflow.biletflow.ordercheckout.domain.checkout;

import com.biletflow.biletflow.common.domain.*;
import java.util.Objects;
import java.util.UUID;
import com.biletflow.biletflow.common.domain.Money;

public final class GeneralAdmissionCheckoutItem implements CheckoutItemMode {
    private final UUID ticketTypeId;
    private final Money price;

    // Constructor
    public GeneralAdmissionCheckoutItem(UUID ticketTypeId, Money price) {
        this.ticketTypeId = Objects.requireNonNull(ticketTypeId, "TicketTypeId can not be null!");
        this.price = Objects.requireNonNull(price, "Price can not be null!");
    }

    public static GeneralAdmissionCheckoutItem createNew(UUID ticketTypeId, Money price) {
        return new GeneralAdmissionCheckoutItem(ticketTypeId, price);
    }

    // Getters
    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public Money getPrice() {
        return price;
    }
}
