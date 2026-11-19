package com.biletflow.biletflow.ticketinventory.application.ticket;

import com.biletflow.biletflow.common.application.EntityNotFoundException;

public class TicketNotFoundException extends EntityNotFoundException {

    public TicketNotFoundException(String message) {
        super(message);
    }
}
