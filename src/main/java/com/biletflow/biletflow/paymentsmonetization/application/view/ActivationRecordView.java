package com.biletflow.biletflow.paymentsmonetization.application.view;

import com.biletflow.biletflow.paymentsmonetization.application.ActivationRepository;

import java.time.Instant;
import java.util.UUID;

/** Administrator view of a paid-sales activation record. */
public record ActivationRecordView(
    UUID eventId,
    long organizerId,
    String status,
    UUID paymentId,
    Instant createdAt,
    Instant suspendedAt,
    String suspensionReason,
    boolean verificationSimulated,
    String termsVersion,
    boolean sandbox
) {
    public static ActivationRecordView from(ActivationRepository.Activation activation) {
        var aggregate = activation.aggregate();
        return new ActivationRecordView(
            aggregate.getEventId(),
            aggregate.getOrganizerId(),
            aggregate.getStatus().name(),
            activation.paymentId(),
            aggregate.getCreatedAt(),
            aggregate.getSuspendedAt(),
            aggregate.getSuspensionReason(),
            activation.verificationSimulated(),
            activation.termsVersion(),
            true
        );
    }
}
