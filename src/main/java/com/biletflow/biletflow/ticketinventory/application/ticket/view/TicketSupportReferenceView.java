package com.biletflow.biletflow.ticketinventory.application.ticket.view;

import java.util.UUID;

/** Minimal cross-context ticket ownership/context projection for Support. */
public record TicketSupportReferenceView(UUID ticketId, UUID orderId, UUID eventId, Long ownerUserId) {}
