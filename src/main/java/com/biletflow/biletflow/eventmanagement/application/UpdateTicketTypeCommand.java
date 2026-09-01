package com.biletflow.biletflow.eventmanagement.application;

import com.biletflow.biletflow.eventmanagement.domain.SalesWindow;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import com.biletflow.biletflow.eventmanagement.domain.TicketTypeId;
import java.util.Objects;
import java.util.UUID;

public record UpdateTicketTypeCommand(
    SocialEventId eventId,
    TicketTypeId ticketTypeId,
    String name,
    String description,
    SalesWindow salesWindow,
    int quantity
) {
    public UpdateTicketTypeCommand {
        Objects.requireNonNull(eventId, "SocialEventId cannot be null");
        Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(salesWindow, "Sales window cannot be null");
    }
}
