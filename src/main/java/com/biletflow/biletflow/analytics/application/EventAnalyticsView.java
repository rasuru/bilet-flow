package com.biletflow.biletflow.analytics.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EventAnalyticsView(
    UUID eventId,
    long capacity,
    long remaining,
    long sold,
    long refunded,
    long checkedIn,
    BigDecimal attendanceRatePercent,
    BigDecimal grossSales,
    BigDecimal discountTotal,
    BigDecimal netRevenue,
    BigDecimal refundAmount,
    String currency,
    List<DailySales> salesOverTime,
    List<TicketTypeSales> ticketTypes,
    List<PromotionSales> campaigns,
    List<AttendeeSales> ticketHolders
) {
    public EventAnalyticsView {
        salesOverTime = List.copyOf(salesOverTime);
        ticketTypes = List.copyOf(ticketTypes);
        campaigns = List.copyOf(campaigns);
        ticketHolders = List.copyOf(ticketHolders);
    }

    public EventAnalyticsView(UUID eventId, long capacity, long sold, long refunded, long checkedIn,
                              BigDecimal attendanceRatePercent, BigDecimal netRevenue, String currency,
                              List<DailySales> salesOverTime, List<TicketTypeSales> ticketTypes,
                              List<PromotionSales> campaigns) {
        this(eventId, capacity, Math.max(0, capacity - sold), sold, refunded, checkedIn, attendanceRatePercent,
            netRevenue, BigDecimal.ZERO, netRevenue, BigDecimal.ZERO, currency, salesOverTime, ticketTypes, campaigns, List.of());
    }

    public record DailySales(LocalDate date, long ticketsSold, BigDecimal revenue) {}
    public record TicketTypeSales(UUID ticketTypeId, String name, long capacity, long sold, long refunded, long checkedIn, BigDecimal revenue) {}
    public record PromotionSales(UUID promotionId, long ticketsSold, BigDecimal revenue) {}
    public record AttendeeSales(String attendeeEmail, long tickets, long checkedIn, long refunded) {}
}
