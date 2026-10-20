package com.biletflow.biletflow.eventmanagement.external;

import com.biletflow.biletflow.eventmanagement.application.cancellation.CancellationRecipients;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.ticketinventory.ohs.EventCancellationRecipientsOhs;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CancellationRecipientsAdapter implements CancellationRecipients {

    private final EventCancellationRecipientsOhs tickets;
    private final IamOhs users;

    public CancellationRecipientsAdapter(EventCancellationRecipientsOhs tickets, IamOhs users) {
        this.tickets = Objects.requireNonNull(tickets);
        this.users = Objects.requireNonNull(users);
    }

    @Override
    public List<String> attendees(UUID eventId) {
        return tickets.attendeeEmails(eventId);
    }

    @Override
    public Optional<String> verifiedEmail(long userId) {
        return users.findVerifiedUserEmail(userId).map(email -> email.email());
    }
}
