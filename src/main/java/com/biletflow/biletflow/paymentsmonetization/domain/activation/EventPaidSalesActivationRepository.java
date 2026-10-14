package com.biletflow.biletflow.paymentsmonetization.domain.activation;

import java.util.Optional;
import java.util.UUID;

public interface EventPaidSalesActivationRepository {
    EventPaidSalesActivation save(EventPaidSalesActivation record);

    Optional<EventPaidSalesActivation> findByEventId(UUID eventId);
}
