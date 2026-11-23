package com.biletflow.biletflow.ticketinventory.external.notification;

import com.biletflow.biletflow.eventmanagement.application.notification.EventUpdateRecipientPort;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketRepository;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Anti-corruption adapter: Event Management receives only email addresses, never Ticket aggregates. */
@Component
@Transactional(readOnly = true)
public class TicketEventUpdateRecipientAdapter implements EventUpdateRecipientPort {

    private final TicketRepository tickets;

    public TicketEventUpdateRecipientAdapter(TicketRepository tickets) {
        this.tickets = Objects.requireNonNull(tickets);
    }

    @Override
    public List<String> findRecipients(UUID eventId) {
        return tickets.findAllByEventId(eventId).stream()
            .filter(ticket -> ticket.receivesEventCancellationNotice())
            .map(ticket -> ticket.getAttendeeEmail().value().trim())
            .filter(email -> !email.isEmpty())
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toMap(email -> email.toLowerCase(Locale.ROOT), email -> email, (first, ignored) -> first, TreeMap::new),
                map -> List.copyOf(map.values())
            ));
    }
}
