package com.biletflow.biletflow.ticketinventory.application.delivery;

import java.util.UUID;

/** Synchronous delivery; failures must propagate so the durable worker can retry. */
public interface TicketDeliverySender {
    void send(TicketDocument document, UUID messageId);
}
