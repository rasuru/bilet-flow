package com.biletflow.biletflow.support.persistence.supportcase.entity;

import com.biletflow.biletflow.support.domain.supportcase.MessageAuthorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.UUID;

@Embeddable
public class SupportMessageJpaEmbeddable {

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private Long authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, updatable = false, length = 32)
    private MessageAuthorType authorType;

    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "text")
    private String content;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected SupportMessageJpaEmbeddable() {}

    public SupportMessageJpaEmbeddable(UUID id, Long authorUserId, MessageAuthorType authorType, String content, Instant sentAt) {
        this.id = id;
        this.authorUserId = authorUserId;
        this.authorType = authorType;
        this.content = content;
        this.sentAt = sentAt;
    }

    public UUID getId() {
        return id;
    }

    public Long getAuthorUserId() {
        return authorUserId;
    }

    public MessageAuthorType getAuthorType() {
        return authorType;
    }

    public String getContent() {
        return content;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
