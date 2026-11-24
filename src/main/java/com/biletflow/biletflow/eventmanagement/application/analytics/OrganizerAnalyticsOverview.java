package com.biletflow.biletflow.eventmanagement.application.analytics;

import com.biletflow.biletflow.eventmanagement.application.analytics.OrganizerAnalyticsView.Attendance;
import com.biletflow.biletflow.eventmanagement.application.analytics.OrganizerAnalyticsView.Sales;

public record OrganizerAnalyticsOverview(Inventory inventory, Sales sales, Attendance attendance, Campaigns campaigns) {
    public record Inventory(long capacity, long sold, long remaining, long held) {}

    public record Campaigns(long redemptions, Sales sales) {}
}
