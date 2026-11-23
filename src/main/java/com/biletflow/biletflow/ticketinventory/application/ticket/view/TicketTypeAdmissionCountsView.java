package com.biletflow.biletflow.ticketinventory.application.ticket.view;

import java.util.UUID;

public record TicketTypeAdmissionCountsView(
    UUID ticketTypeId,
    String ticketTypeName,
    long refundedCount,
    long checkedInCount
) {}
