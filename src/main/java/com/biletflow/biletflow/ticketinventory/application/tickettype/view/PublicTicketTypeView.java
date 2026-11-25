package com.biletflow.biletflow.ticketinventory.application.tickettype.view;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.TicketPurchaseAvailability;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeStatus;
import java.time.Instant;
import java.util.UUID;

public record PublicTicketTypeView(
    UUID id,
    UUID eventId,
    String name,
    String description,
    Money price,
    int maxPerOrder,
    Instant salesStartAt,
    Instant salesEndAt,
    TicketTypeStatus status,
    String priceCategory,
    Integer totalCapacity,
    Integer reserved,
    Integer sold,
    Integer available,
    boolean purchasable,
    TicketPurchaseAvailability purchaseStatus
) {
    public static PublicTicketTypeView from(TicketTypeView type, TicketPurchaseAvailability availability) {
        return new PublicTicketTypeView(
            type.id(), type.eventId(), type.name(), type.description(), type.price(), type.maxPerOrder(),
            type.salesStartAt(), type.salesEndAt(), type.status(), type.priceCategory(), type.totalCapacity(),
            type.reserved(), type.sold(), type.available(), availability == TicketPurchaseAvailability.AVAILABLE, availability
        );
    }
}

