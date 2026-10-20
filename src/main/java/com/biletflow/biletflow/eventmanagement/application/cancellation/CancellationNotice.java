package com.biletflow.biletflow.eventmanagement.application.cancellation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Immutable delivery snapshot. The message ID is stable across event redelivery and SMTP retries. */
public record CancellationNotice(
    UUID id,
    UUID eventId,
    String recipient,
    String title,
    Instant startsAt,
    Instant endsAt,
    Instant cancelledAt
) {
    public CancellationNotice {
        Objects.requireNonNull(id);
        Objects.requireNonNull(eventId);
        recipient = Objects.requireNonNull(recipient).trim().toLowerCase(Locale.ROOT);
        Objects.requireNonNull(title);
        Objects.requireNonNull(startsAt);
        Objects.requireNonNull(endsAt);
        Objects.requireNonNull(cancelledAt);
        if (recipient.isBlank()) throw new IllegalArgumentException("Cancellation notice recipient is blank");
    }

    public static CancellationNotice forRecipient(
        UUID eventId,
        String recipient,
        String title,
        Instant startsAt,
        Instant endsAt,
        Instant cancelledAt
    ) {
        String email = recipient.trim().toLowerCase(Locale.ROOT);
        UUID id = UUID.nameUUIDFromBytes(("event-cancellation:" + eventId + ":" + email).getBytes(StandardCharsets.UTF_8));
        return new CancellationNotice(id, eventId, email, title, startsAt, endsAt, cancelledAt);
    }
}
