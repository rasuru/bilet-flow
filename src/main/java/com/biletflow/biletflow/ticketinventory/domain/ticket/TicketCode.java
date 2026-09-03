package com.biletflow.biletflow.ticketinventory.domain.ticket;

import java.util.Objects;
import java.util.UUID;

/**
 * Opaque public ticket reference.
 *
 * The QR presented to a scanner should contain this value plus a server-generated
 * cryptographic signature/MAC. Do not trust ticket status, price, seat, etc. from
 * QR claims; load authoritative Ticket state after signature verification.
 */
public record TicketCode(UUID value) {
    public TicketCode {
        Objects.requireNonNull(value, "TicketCode cannot be null");
    }

    public static TicketCode generate() {
        return new TicketCode(UUID.randomUUID());
    }
}
