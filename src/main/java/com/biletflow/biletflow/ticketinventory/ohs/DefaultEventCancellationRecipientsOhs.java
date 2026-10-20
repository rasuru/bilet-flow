package com.biletflow.biletflow.ticketinventory.ohs;

import com.biletflow.biletflow.ticketinventory.domain.ticket.Ticket;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultEventCancellationRecipientsOhs implements EventCancellationRecipientsOhs {

    private final TicketRepository tickets;

    public DefaultEventCancellationRecipientsOhs(TicketRepository tickets) {
        this.tickets = Objects.requireNonNull(tickets);
    }

    @Override
    public List<String> attendeeEmails(UUID eventId) {
        return tickets
            .findAllByEventId(eventId)
            .stream()
            .filter(Ticket::receivesEventCancellationNotice)
            .map(ticket -> ticket.getAttendeeEmail().value())
            .distinct()
            .toList();
    }
}
