package com.biletflow.biletflow.paymentsmonetization.ohs;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface RefundAnalyticsOhs {
    Map<UUID, BigDecimal> confirmedTicketRefunds(UUID eventId);
}
