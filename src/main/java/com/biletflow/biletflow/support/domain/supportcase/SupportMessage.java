package com.biletflow.biletflow.support.domain.supportcase;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SupportMessage(
    UUID id,
    Long authorUserId,
    MessageAuthorType authorType,
    String content,
    Instant sentAt
) {

    public SupportMessage {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(authorUserId, "authorUserId cannot be null");
        Objects.requireNonNull(authorType, "authorType cannot be null");
        Objects.requireNonNull(sentAt, "sentAt cannot be null");

        if (authorUserId <= 0) {
            throw new IllegalArgumentException("authorUserId must be positive");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content cannot be empty");
        }

        content = content.trim();
    }

    public static SupportMessage create(
        Long authorUserId,
        MessageAuthorType authorType,
        String content,
        Instant sentAt
    ) {
        return new SupportMessage(
            UUID.randomUUID(), authorUserId, authorType, content, sentAt
        );
    }
}
