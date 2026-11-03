package com.biletflow.biletflow.ticketinventory.ohs;

import java.util.UUID;

public interface PaidSalesInventoryOhs {
    boolean hasPaidTicketType(UUID eventId);
}
