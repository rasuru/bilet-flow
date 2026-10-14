package com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivationFeePaymentRepository {
    ActivationFeePayment save(ActivationFeePayment payment);

    Optional<ActivationFeePayment> findById(ActivationFeePaymentId id);

    List<ActivationFeePayment> findAllByEventId(UUID eventId);
}
