package com.biletflow.biletflow.ticketinventory.ohs;

import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeStatus;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultPaidSalesInventoryOhs implements PaidSalesInventoryOhs {

    private final TicketTypeRepository ticketTypes;

    public DefaultPaidSalesInventoryOhs(TicketTypeRepository ticketTypes) {
        this.ticketTypes = Objects.requireNonNull(ticketTypes, "ticketTypes cannot be null");
    }

    @Override
    public boolean hasPaidTicketType(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        return ticketTypes
            .findAllByEventId(eventId)
            .stream()
            .anyMatch(type -> type.getStatus() != TicketTypeStatus.CLOSED && !type.getPrice().isZero());
    }
}
