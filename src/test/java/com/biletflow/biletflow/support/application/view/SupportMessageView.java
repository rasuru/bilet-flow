package com.biletflow.biletflow.support.application.view;

import com.biletflow.biletflow.support.domain.supportcase.MessageAuthorType;
import com.biletflow.biletflow.support.domain.supportcase.SupportMessage;
import java.time.Instant;
import java.util.UUID;

public record SupportMessageView(
    UUID id,
    Long authorUserId,
    MessageAuthorType authorType,
    String content,
    Instant sentAt
) {
    public static SupportMessageView from(SupportMessage message) {
        return new SupportMessageView(
            message.id(), message.authorUserId(), message.authorType(), message.content(), message.sentAt()
        );
    }
}
