package com.biletflow.biletflow.eventmanagement.infrastructure.cancellation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.eventmanagement.application.cancellation.CancellationNotice;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SmtpCancellationNoticeSenderTest {

    @Test
    void sendsCancellationCalendarWithStableUidEscapedContentAndUtf8LineFolding() {
        var emails = mock(EmailSender.class);
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        var event = UUID.randomUUID();
        String title = "Қазақша кездесу ".repeat(12) + ",;\r\nBEGIN:VEVENT";
        var notice = CancellationNotice.forRecipient(
            event,
            "attendee@example.com",
            title,
            now.plusSeconds(3600),
            now.plusSeconds(7200),
            now
        );
        new SmtpCancellationNoticeSender(emails).send(notice);
        var argument = ArgumentCaptor.forClass(EmailSender.Email.class);
        verify(emails).send(argument.capture());
        var email = argument.getValue();
        assertEquals(notice.id(), email.id());
        assertEquals("text/calendar; charset=UTF-8", email.attachmentContentType());
        String calendar = new String(email.attachment(), StandardCharsets.UTF_8);
        for (String line : calendar.split("\r\n")) assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75);
        String unfolded = calendar.replace("\r\n ", "");
        assertTrue(unfolded.contains("UID:" + event + "@biletflow.invalid\r\n"));
        assertTrue(unfolded.contains("STATUS:CANCELLED\r\n"));
        assertTrue(unfolded.contains("SUMMARY:" + title.replace("\r\n", "\\n").replace(";", "\\;").replace(",", "\\,")));
        assertEquals(
            1,
            calendar
                .lines()
                .filter(line -> line.equals("BEGIN:VEVENT"))
                .count()
        );
        assertTrue(email.text().contains("does not confirm a refund"));
    }
}
