package com.biletflow.biletflow.ordercheckout.ohs;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderAnalyticsOhs {
    List<SaleLine> issuedSaleLines(UUID eventId, Instant from, Instant until);

    record SaleLine(UUID orderId, Instant orderedAt, UUID ticketTypeId, long quantity, BigDecimal grossKzt, BigDecimal orderDiscountKzt) {}
}
