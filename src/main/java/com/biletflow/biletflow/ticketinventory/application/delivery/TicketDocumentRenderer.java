package com.biletflow.biletflow.ticketinventory.application.delivery;

public interface TicketDocumentRenderer {
    byte[] pdf(TicketDocument document);
    byte[] qr(TicketDocument document);
}
