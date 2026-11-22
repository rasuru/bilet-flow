package com.biletflow.biletflow.eventmanagement.domain.report;

import com.biletflow.biletflow.eventmanagement.domain.exceptions.InvalidEventStateException;
import java.time.Instant;
import java.util.UUID;

public record EventReport(
    UUID id,
    UUID eventId,
    long reporterId,
    String reason,
    Instant submittedAt,
    Status status,
    Long reviewerId,
    Instant reviewedAt,
    String reviewNote
) {
    public enum Status {
        OPEN,
        DISMISSED,
        CONFIRMED,
    }

    public enum Decision {
        DISMISS,
        CONFIRM,
    }

    public static EventReport submit(UUID eventId, long reporterId, String reason, Instant now) {
        return new EventReport(UUID.randomUUID(), eventId, reporterId, requireText(reason, 1000), now, Status.OPEN, null, null, null);
    }

    public EventReport review(Decision decision, long adminId, String note, Instant now) {
        if (decision == null) throw new IllegalArgumentException("A review decision is required");
        String validNote = requireText(note, 1000);
        Status outcome = switch (decision) {
            case DISMISS -> Status.DISMISSED;
            case CONFIRM -> Status.CONFIRMED;
        };
        if (status == outcome) return this;
        if (status != Status.OPEN) throw new InvalidEventStateException("This report has already been reviewed");
        return new EventReport(id, eventId, reporterId, reason, submittedAt, outcome, adminId, now, validNote);
    }

    private static String requireText(String text, int maxLength) {
        if (text == null || text.isBlank() || text.length() > maxLength) {
            throw new IllegalArgumentException("Report text must be non-blank and at most " + maxLength + " characters");
        }
        return text.strip();
    }
}
