package com.biletflow.biletflow.payments.domain.activation;

import java.util.Optional;
import java.util.UUID;

public interface ActivationRecordRepository {
    ActivationRecord save(ActivationRecord record);

    Optional<ActivationRecord> findByEventId(UUID eventId);
}
