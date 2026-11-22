package com.biletflow.biletflow.ordercheckout.application.command;

import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs;
import java.util.*;

public record CreateCheckoutCommand(UUID eventId, List<CheckoutInventoryOhs.Selection> selections, String promoCode) {
    public CreateCheckoutCommand(UUID eventId, List<CheckoutInventoryOhs.Selection> selections) {
        this(eventId, selections, null);
    }

    public CreateCheckoutCommand {
        Objects.requireNonNull(eventId);
        selections = List.copyOf(selections);
        if (promoCode != null) promoCode = promoCode.strip().toUpperCase(Locale.ROOT);
    }
}
