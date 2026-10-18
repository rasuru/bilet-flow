package com.biletflow.biletflow.paymentsmonetization.application;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface PaymentLedger {
    void charge(PaymentWork work);
    void refund(PaymentWork work, UUID refundId);
    Map<String, BigDecimal> summary(long organizerId);
}
