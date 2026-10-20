package com.biletflow.biletflow.common.notifications.infrastructure;

import com.biletflow.biletflow.common.notifications.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import tech.jhipster.config.JHipsterProperties;

@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mail;
    private final JHipsterProperties properties;

    public SmtpEmailSender(JavaMailSender mail, JHipsterProperties properties) {
        this.mail = Objects.requireNonNull(mail);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public void send(Email email) {
        try {
            var mime = mail.createMimeMessage();
            var message = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            message.setFrom(properties.getMail().getFrom());
            message.setTo(new InternetAddress(email.recipient(), true));
            message.setSubject(email.subject().replaceAll("[\r\n]+", " "));
            message.setText(email.text(), false);
            message.addAttachment(email.filename(), new ByteArrayResource(email.attachment()), "application/pdf");
            mime.setHeader("Message-ID", "<" + email.id() + "@biletflow.invalid>");
            mail.send(mime);
        } catch (MessagingException failure) {
            throw new MailPreparationException("Email could not be prepared", failure);
        }
    }
}
