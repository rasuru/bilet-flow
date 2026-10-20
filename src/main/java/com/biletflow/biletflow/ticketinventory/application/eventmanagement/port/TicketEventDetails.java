package com.biletflow.biletflow.ticketinventory.application.eventmanagement.port;

import java.time.Instant;

public record TicketEventDetails(
    String title,
    Instant startsAt,
    Instant endsAt,
    String venue,
    String address,
    String seat,
    boolean cancelled
) {}
