package com.biletflow.biletflow.paymentsmonetization.application.view;

import java.math.BigDecimal;
import java.util.Map;

public record RevenueView(boolean sandbox, boolean feesEstimated, String currency, Map<String, BigDecimal> totals) {}
