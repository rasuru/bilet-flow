package com.biletflow.biletflow.promotions.external.ticketinventory;

import com.biletflow.biletflow.promotions.application.ticketinventory.port.TicketInventoryPort;
import com.biletflow.biletflow.ticketinventory.ohs.PromotionsInventoryOhs;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TicketInventoryAcl implements TicketInventoryPort {

    private final PromotionsInventoryOhs promotionsInventoryOhs;

    public TicketInventoryAcl(PromotionsInventoryOhs promotionsInventoryOhs) {
        this.promotionsInventoryOhs = Objects.requireNonNull(promotionsInventoryOhs);
    }

    @Override
    public Set<UUID> ticketTypeIdsOf(UUID eventId) {
        return Set.copyOf(promotionsInventoryOhs.ticketTypeIds(eventId));
    }
}
