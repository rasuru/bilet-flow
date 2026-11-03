package com.biletflow.biletflow.paymentsmonetization.application.view;

import java.math.BigDecimal;
import java.time.Instant;

/** The fee charged to new activation requests */
public record ActivationFeeView(
    BigDecimal amountKzt,
    String currency,
    boolean administratorOverride,
    Long updatedBy,
    Instant updatedAt,
    boolean sandbox
) {}
