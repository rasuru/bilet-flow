package com.biletflow.biletflow.ticketinventory.application.tickettype.port;

import java.util.UUID;

/** Read-only activation fact from Payments & Monetization. */
public interface PaidSalesPort {
    boolean permitsPaidSales(UUID eventId);
}

