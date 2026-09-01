package com.biletflow.biletflow.eventmanagement.application;

import com.biletflow.biletflow.eventmanagement.domain.SalesWindow;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import com.biletflow.biletflow.eventmanagement.domain.TicketPricing;
import java.util.Objects;
import java.util.UUID;

public record CreateTicketTypeCommand(
    SocialEventId eventId,
    String name,
    String description,
    TicketPricing pricing,
    int quantity,
    SalesWindow salesWindow,
    int maxPerOrder
) {
    public CreateTicketTypeCommand {
        Objects.requireNonNull(eventId, "SocialEventId cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(pricing, "Pricing cannot be null");
        Objects.requireNonNull(salesWindow, "Sales window cannot be null");
    }
}
