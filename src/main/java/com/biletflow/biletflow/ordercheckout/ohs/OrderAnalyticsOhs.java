package com.biletflow.biletflow.ordercheckout.ohs;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Read-only analytics contract published by Order & Checkout for the organizer dashboard. */
public interface OrderAnalyticsOhs {
    List<OrderSnapshot> findEventOrders(UUID eventId);

    record OrderSnapshot(
        UUID orderId,
        String status,
        UUID promotionId,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        String currency,
        List<OrderLine> lines
    ) {
        public OrderSnapshot {
            lines = List.copyOf(lines);
        }
    }

    record OrderLine(UUID ticketTypeId, BigDecimal priceAmount, String currency) {}
}
