package com.biletflow.biletflow.eventmanagement.ohs.dto;

import java.time.Instant;

/** Event data available to holders of an issued ticket, including unlisted/private events. */
public record TicketEventDetailsView(
    String title,
    Instant startsAt,
    Instant endsAt,
    String venue,
    String address,
    String seat,
    boolean cancelled
) {}
