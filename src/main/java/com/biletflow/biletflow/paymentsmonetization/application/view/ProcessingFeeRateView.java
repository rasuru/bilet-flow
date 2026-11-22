package com.biletflow.biletflow.paymentsmonetization.application.view;

import java.math.BigDecimal;
import java.time.Instant;

public record ProcessingFeeRateView(
    BigDecimal ratePercent,
    boolean administratorOverride,
    Long updatedBy,
    Instant updatedAt,
    boolean sandbox
) {}
