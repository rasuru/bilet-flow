package com.biletflow.biletflow.ordercheckout.application.eventmanagement.result;

import com.biletflow.biletflow.ordercheckout.application.ticketinventory.command.HoldReference;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record TicketTypeConfiguration(
    UUID ticketTypeId,
    UUID eventId,
    Money price,
    int maxPerOrder,
    String priceCategory)
{ }
