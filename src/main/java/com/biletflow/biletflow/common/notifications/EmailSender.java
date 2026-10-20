package com.biletflow.biletflow.common.notifications;

import java.util.Objects;
import java.util.UUID;

/** Synchronous transport port: successful return means SMTP accepted the message, not inbox receipt. */
public interface EmailSender {
    record Email(UUID id, String recipient, String subject, String text, String filename, byte[] attachment) {
        public Email {
            Objects.requireNonNull(id);
            Objects.requireNonNull(recipient);
            Objects.requireNonNull(subject);
            Objects.requireNonNull(text);
            Objects.requireNonNull(filename);
            attachment = attachment.clone();
        }

        @Override
        public byte[] attachment() {
            return attachment.clone();
        }
    }

    void send(Email message);
}
