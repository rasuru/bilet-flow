package com.biletflow.biletflow.ticketinventory.infrastructure.delivery;

import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.ticketinventory.application.delivery.*;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SmtpTicketDeliverySender implements TicketDeliverySender {

    private final EmailSender emails;
    private final TicketDocumentRenderer renderer;

    public SmtpTicketDeliverySender(EmailSender emails, TicketDocumentRenderer renderer) {
        this.emails = Objects.requireNonNull(emails);
        this.renderer = Objects.requireNonNull(renderer);
    }

    @Override
    public void send(TicketDocument document, UUID messageId) {
        var ticket = document.ticket();
        var event = document.event();
        String text =
            "Your registration is confirmed. Your ticket is attached as a printable PDF.\n\n" +
            "Event: " +
            event.title() +
            "\nVenue: " +
            event.venue() +
            "\nAddress: " +
            event.address() +
            "\nSeat: " +
            event.seat() +
            "\nTicket ID: " +
            ticket.id() +
            "\nOrder reference: " +
            ticket.orderId() +
            "\n\nShow the QR code on your phone or print the PDF. Both copies use the same admission. " +
            "Keep the QR code private. Admission is checked online; refunded or cancelled tickets cannot be used. " +
            "To claim the ticket in your account, use ticket ID " +
            ticket.id() +
            " with this verified email address.\n";
        emails.send(
            new EmailSender.Email(
                messageId,
                ticket.attendeeEmail(),
                "BiletFlow registration confirmed: " + event.title(),
                text,
                "ticket-" + ticket.id() + ".pdf",
                renderer.pdf(document)
            )
        );
    }
}
