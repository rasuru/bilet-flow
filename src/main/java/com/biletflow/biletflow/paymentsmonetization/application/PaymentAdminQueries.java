package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivationStatus;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentPurpose;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentStatus;
import java.util.List;
import java.util.UUID;

public interface PaymentAdminQueries {
    record PaymentFilter(PaymentStatus status, PaymentPurpose purpose, UUID eventId, Long payerId) {}

    /** Payment IDs, newest payment deadline first. */
    List<UUID> findPaymentIds(PaymentFilter filter, int offset, int limit);

    /** Activation records, newest first. */
    List<ActivationRepository.Activation> findActivations(EventPaidSalesActivationStatus status, int offset, int limit);
}
