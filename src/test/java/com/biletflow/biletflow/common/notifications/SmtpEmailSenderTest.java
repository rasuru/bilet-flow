package com.biletflow.biletflow.common.notifications;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.notifications.infrastructure.SmtpEmailSender;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import tech.jhipster.config.JHipsterProperties;

class SmtpEmailSenderTest {

    @Test
    void sendsOnlyToRecordedRecipientWithPdfAndStableMessageId() throws Exception {
        var mail = mock(JavaMailSender.class);
        var mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mail.createMimeMessage()).thenReturn(mime);
        var settings = new JHipsterProperties();
        settings.getMail().setFrom("tickets@example.com");
        var email = new EmailSender.Email(
            UUID.randomUUID(),
            "recipient@example.com",
            "Confirmation",
            "Your registration is confirmed",
            "ticket.pdf",
            new byte[] { 1, 2, 3 }
        );
        new SmtpEmailSender(mail, settings).send(email);
        assertEquals("recipient@example.com", mime.getAllRecipients()[0].toString());
        assertEquals(1, mime.getAllRecipients().length);
        assertEquals("<" + email.id() + "@biletflow.invalid>", mime.getHeader("Message-ID")[0]);
        var mixed = (Multipart) mime.getContent();
        var attachment = mixed.getBodyPart(mixed.getCount() - 1);
        assertEquals("ticket.pdf", attachment.getFileName());
        assertArrayEquals(new byte[] { 1, 2, 3 }, attachment.getInputStream().readAllBytes());
        verify(mail).send(mime);
    }

    @Test
    void mailFailurePropagatesToDurableWorker() {
        var mail = mock(JavaMailSender.class);
        var mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mail.createMimeMessage()).thenReturn(mime);
        doThrow(new MailSendException("offline")).when(mail).send(mime);
        var settings = new JHipsterProperties();
        settings.getMail().setFrom("tickets@example.com");
        var sender = new SmtpEmailSender(mail, settings);
        assertThrows(MailSendException.class, () ->
            sender.send(
                new EmailSender.Email(UUID.randomUUID(), "recipient@example.com", "Ticket", "Confirmed", "ticket.pdf", new byte[] { 1 })
            )
        );
    }
}
