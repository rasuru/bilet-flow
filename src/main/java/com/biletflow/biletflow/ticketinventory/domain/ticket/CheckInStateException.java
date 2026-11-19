package com.biletflow.biletflow.ticketinventory.domain.ticket;

public class CheckInStateException extends IllegalStateException {

    private final TicketStatus status;

    public CheckInStateException(String message, TicketStatus status) {
        super(message);
        this.status = status;
    }

    public TicketStatus status() {
        return status;
    }
}
