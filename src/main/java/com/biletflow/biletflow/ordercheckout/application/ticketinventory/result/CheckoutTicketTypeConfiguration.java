package com.biletflow.biletflow.ordercheckout.application.ticketinventory.result;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.biletflow.biletflow.common.domain.Money;

public record CheckoutTicketTypeConfiguration(
    UUID ticketTypeId,
    UUID eventId,
    Money price,
    int maxPerOrder,
    String priceCategory)
{ }
