package com.biletflow.biletflow.ticketinventory.ohs;

import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultPromotionsInventoryOhs implements PromotionsInventoryOhs {

    private final TicketTypeRepository ticketTypes;

    public DefaultPromotionsInventoryOhs(TicketTypeRepository ticketTypes) {
        this.ticketTypes = Objects.requireNonNull(ticketTypes, "ticketTypes cannot be null");
    }

    @Override
    public Set<UUID> ticketTypeIds(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        return ticketTypes
            .findAllByEventId(eventId)
            .stream()
            .map(type -> type.getId().value())
            .collect(Collectors.toUnmodifiableSet());
    }
}
