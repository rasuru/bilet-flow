package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivation;
import java.util.Optional;
import java.util.UUID;

public interface ActivationRepository {
    record Activation(EventPaidSalesActivation aggregate, UUID paymentId, boolean verificationSimulated, String termsVersion) {}

    Optional<Activation> find(UUID eventId);
    void save(Activation activation);
}
