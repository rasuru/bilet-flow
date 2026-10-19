package com.biletflow.biletflow.ordercheckout.application.command;

import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs;
import java.util.*;

public record CreateCheckoutCommand(UUID eventId, List<CheckoutInventoryOhs.Selection> selections) {
    public CreateCheckoutCommand {
        Objects.requireNonNull(eventId);
        selections = List.copyOf(selections);
    }
}
