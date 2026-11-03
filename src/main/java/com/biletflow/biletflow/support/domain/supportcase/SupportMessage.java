package com.biletflow.biletflow.support.domain.supportcase;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SupportMessage(UUID id, Long authorUserId, MessageAuthorType authorType, String content, Instant sentAt) {

    public SupportMessage {
        Objects.requireNonNull(id, "SupportMessage id cannot be null");
        Objects.requireNonNull(authorUserId, "SupportMessage authorUserId cannot be null");
        Objects.requireNonNull(authorType, "SupportMessage authorType cannot be null");
        Objects.requireNonNull(sentAt, "SupportMessage sentAt cannot be null");

        if (authorUserId <= 0) {
            throw new IllegalArgumentException("SupportMessage authorUserId must be positive");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("SupportMessage content cannot be empty");
        }

        content = content.trim();
    }

    public static SupportMessage create(Long authorUserId, MessageAuthorType authorType, String content, Instant sentAt) {
        return new SupportMessage(UUID.randomUUID(), authorUserId, authorType, content, sentAt);
    }
}
