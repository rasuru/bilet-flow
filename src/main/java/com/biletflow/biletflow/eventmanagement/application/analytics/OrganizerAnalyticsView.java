package com.biletflow.biletflow.eventmanagement.application.analytics;

import com.biletflow.biletflow.ticketinventory.ohs.TicketAnalyticsOhs.Inventory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OrganizerAnalyticsView(
    Inventory inventory,
    Sales sales,
    List<DailySales> dailySales,
    List<TicketSales> ticketSales,
    List<CampaignSales> campaignSales,
    Attendance attendance,
    long ticketsWithoutOrderDate
) {
    public record Sales(long tickets, BigDecimal grossKzt, BigDecimal discountsKzt, BigDecimal refundsKzt, BigDecimal netKzt) {}

    public record DailySales(LocalDate date, Sales sales) {}

    public record TicketSales(UUID id, Sales sales) {}

    public record CampaignSales(UUID id, String name, long redemptions, Sales sales, List<CodeSales> codes) {}

    public record CodeSales(String code, long redemptions, Sales sales) {}

    public record Attendance(long registered, long checkedIn, long absent) {}
}
