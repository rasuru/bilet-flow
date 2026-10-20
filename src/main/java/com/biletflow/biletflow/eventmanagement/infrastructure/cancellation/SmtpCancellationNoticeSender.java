package com.biletflow.biletflow.eventmanagement.infrastructure.cancellation;

import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.eventmanagement.application.cancellation.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class SmtpCancellationNoticeSender implements CancellationNoticeSender {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private final EmailSender emails;

    public SmtpCancellationNoticeSender(EmailSender emails) {
        this.emails = Objects.requireNonNull(emails);
    }

    @Override
    public void send(CancellationNotice notice) {
        String text =
            "Event cancelled: " +
            notice.title() +
            "\n\n" +
            "This event will not take place. Its tickets cannot be used for admission.\n" +
            "The attached calendar file marks the event as cancelled.\n\n" +
            "This notice does not confirm a refund. Contact the organizer about refund eligibility and status.\n";
        String calendar = String.join(
            "\r\n",
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//BiletFlow//Events//EN",
            "METHOD:PUBLISH",
            "BEGIN:VEVENT",
            "UID:" + notice.eventId() + "@biletflow.invalid",
            "SEQUENCE:1",
            "DTSTAMP:" + UTC.format(notice.cancelledAt()),
            "LAST-MODIFIED:" + UTC.format(notice.cancelledAt()),
            "DTSTART:" + UTC.format(notice.startsAt()),
            "DTEND:" + UTC.format(notice.endsAt()),
            "SUMMARY:" + escape(notice.title()),
            "STATUS:CANCELLED",
            "END:VEVENT",
            "END:VCALENDAR",
            ""
        );
        emails.send(
            new EmailSender.Email(
                notice.id(),
                notice.recipient(),
                "BiletFlow event cancelled: " + notice.title(),
                text,
                "event-" + notice.eventId() + "-cancelled.ics",
                fold(calendar).getBytes(StandardCharsets.UTF_8),
                "text/calendar; charset=UTF-8"
            )
        );
    }

    private static String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\n", "\\n")
            .replace(";", "\\;")
            .replace(",", "\\,");
    }

    /** RFC 5545 physical lines are at most 75 UTF-8 octets, including continuation whitespace. */
    private static String fold(String calendar) {
        var out = new StringBuilder();
        for (String line : calendar.split("\r\n", -1)) {
            if (!out.isEmpty()) out.append("\r\n");
            int bytes = 0;
            for (int cp : line.codePoints().toArray()) {
                String c = new String(Character.toChars(cp));
                int size = c.getBytes(StandardCharsets.UTF_8).length;
                if (bytes + size > 75) {
                    out.append("\r\n ");
                    bytes = 1;
                }
                out.append(c);
                bytes += size;
            }
        }
        return out.toString();
    }
}
