package com.biletflow.biletflow.eventmanagement.application;

import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import com.biletflow.biletflow.eventmanagement.domain.TicketTypeId;
import java.util.Objects;
import java.util.UUID;

public record HideTicketTypeCommand(SocialEventId eventId, TicketTypeId ticketTypeId) {
    public HideTicketTypeCommand {
        Objects.requireNonNull(eventId, "SocialEventId cannot be null");
        Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null");
    }
}
