package com.biletflow.biletflow.ticketinventory.application.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EventAnalyticsView(
    UUID eventId,
    long capacity,
    long sold,
    long refunded,
    long checkedIn,
    BigDecimal attendanceRatePercent,
    BigDecimal netRevenue,
    String currency,
    List<DailySales> salesOverTime,
    List<TicketTypeSales> ticketTypes,
    List<PromotionSales> campaigns
) {
    public EventAnalyticsView {
        salesOverTime = List.copyOf(salesOverTime);
        ticketTypes = List.copyOf(ticketTypes);
        campaigns = List.copyOf(campaigns);
    }

    public record DailySales(LocalDate date, long ticketsSold, BigDecimal revenue) {}
    public record TicketTypeSales(UUID ticketTypeId, String name, long capacity, long sold, long refunded, long checkedIn, BigDecimal revenue) {}
    public record PromotionSales(UUID promotionId, long ticketsSold, BigDecimal revenue) {}
}
