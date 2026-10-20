package com.biletflow.biletflow.common.notifications;

import java.util.Objects;
import java.util.UUID;

/** Synchronous transport port: successful return means SMTP accepted the message, not inbox receipt. */
public interface EmailSender {
    record Email(UUID id, String recipient, String subject, String text, String filename, byte[] attachment, String attachmentContentType) {
        public Email(UUID id, String recipient, String subject, String text) {
            this(id, recipient, subject, text, null, null, "application/octet-stream");
        }

        public Email(UUID id, String recipient, String subject, String text, String filename, byte[] attachment) {
            this(id, recipient, subject, text, filename, attachment, "application/pdf");
        }

        public Email {
            Objects.requireNonNull(attachmentContentType);
            Objects.requireNonNull(id);
            Objects.requireNonNull(recipient);
            Objects.requireNonNull(subject);
            Objects.requireNonNull(text);
            if ((filename == null) != (attachment == null)) throw new IllegalArgumentException(
                "Attachment requires both filename and content"
            );
            attachment = attachment == null ? null : attachment.clone();
        }

        @Override
        public byte[] attachment() {
            return attachment == null ? null : attachment.clone();
        }
    }

    void send(Email message);
}
