package com.biletflow.biletflow.common.notifications.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.notifications.EmailSender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import tech.jhipster.config.JHipsterProperties;

class SmtpEmailSenderTest {

    @Test
    void plainReceiptHasNoAttachmentAndPreservesStableDeliveryId() throws Exception {
        var mail = mock(JavaMailSender.class);
        var mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mail.createMimeMessage()).thenReturn(mime);
        var properties = new JHipsterProperties();
        properties.getMail().setFrom("noreply@example.com");
        var id = UUID.randomUUID();
        new SmtpEmailSender(mail, properties).send(new EmailSender.Email(id, "buyer@example.com", "Refund\r\nconfirmed", "Refund details"));
        verify(mail).send(mime);
        assertEquals("buyer@example.com", mime.getAllRecipients()[0].toString());
        assertEquals("Refund confirmed", mime.getSubject());
        assertEquals("Refund details", mime.getContent());
        assertEquals("<" + id + "@biletflow.invalid>", mime.getHeader("Message-ID", null));
    }

    @Test
    void existingPdfAndCalendarAttachmentsRetainTheirBytesAndContentTypes() throws Exception {
        for (String type : new String[] { "application/pdf", "text/calendar; charset=UTF-8" }) {
            var mail = mock(JavaMailSender.class);
            var mime = new MimeMessage(Session.getInstance(new Properties()));
            when(mail.createMimeMessage()).thenReturn(mime);
            var properties = new JHipsterProperties();
            properties.getMail().setFrom("noreply@example.com");
            byte[] bytes = "attachment content".getBytes(StandardCharsets.UTF_8);
            new SmtpEmailSender(mail, properties).send(
                new EmailSender.Email(UUID.randomUUID(), "buyer@example.com", "Ticket notice", "Details", "attachment", bytes, type)
            );
            var multipart = assertInstanceOf(MimeMultipart.class, mime.getContent());
            var attachment = multipart.getBodyPart(1);
            assertEquals("attachment", attachment.getFileName());
            assertEquals(type, attachment.getDataHandler().getContentType());
            assertArrayEquals(bytes, attachment.getInputStream().readAllBytes());
            verify(mail).send(mime);
        }
    }
}
