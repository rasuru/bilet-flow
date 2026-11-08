package com.biletflow.biletflow.paymentsmonetization.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** Platform payment settings that administrators change without a restart. */
public interface PaymentSettingsRepository {
    record ActivationFeeSetting(BigDecimal amountKzt, long updatedBy, Instant updatedAt) {}

    /** Empty until an administrator first overrides the configured default. */
    Optional<ActivationFeeSetting> activationFee();

    void saveActivationFee(ActivationFeeSetting setting);

    record ProcessingFeeRateSetting(BigDecimal rate, long updatedBy, Instant updatedAt) {}

    Optional<ProcessingFeeRateSetting> processingFeeRate();

    void saveProcessingFeeRate(ProcessingFeeRateSetting setting);
}
