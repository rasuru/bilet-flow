package com.biletflow.biletflow.ticketinventory.ohs;

import java.util.Set;
import java.util.UUID;

public interface PromotionsInventoryOhs {
    Set<UUID> ticketTypeIds(UUID eventId);
}
