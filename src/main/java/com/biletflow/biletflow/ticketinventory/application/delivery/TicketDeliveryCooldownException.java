package com.biletflow.biletflow.ticketinventory.application.delivery;

/** A completed delivery cannot be resent until the request cooldown has elapsed. */
public class TicketDeliveryCooldownException extends RuntimeException {

    public TicketDeliveryCooldownException() {
        super("Wait at least one minute before requesting another email");
    }
}
