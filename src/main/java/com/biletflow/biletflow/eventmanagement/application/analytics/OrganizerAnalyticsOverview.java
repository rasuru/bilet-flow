package com.biletflow.biletflow.eventmanagement.application.analytics;

import com.biletflow.biletflow.eventmanagement.application.analytics.OrganizerAnalyticsView.Attendance;
import com.biletflow.biletflow.eventmanagement.application.analytics.OrganizerAnalyticsView.Sales;
import java.util.List;
import java.util.UUID;

public record OrganizerAnalyticsOverview(
    Inventory inventory,
    Sales sales,
    Attendance attendance,
    Campaigns campaigns,
    List<EventSales> events
) {
    public record Inventory(long capacity, long sold, long remaining, long held) {}

    public record Campaigns(long redemptions, Sales sales) {}

    public record EventSales(UUID eventId, Sales sales, TicketPricing pricing) {}

    /** Classification by configured ticket prices, independently of current sales availability. */
    public enum TicketPricing {
        PAID,
        FREE,
        MIXED,
        UNCONFIGURED,
    }
}
