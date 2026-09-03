package com.biletflow.biletflow.ticketinventory.domain.tickettype;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketTypeRepository {
    TicketType save(TicketType ticketType);

    Optional<TicketType> findById(TicketTypeId id);

    List<TicketType> findAllByEventId(UUID eventId);
}
