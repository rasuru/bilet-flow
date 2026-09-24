package com.biletflow.biletflow.ticketinventory.application.ticket;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventManagementPort;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.CheckInTicketCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.ReverseCheckInCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketValidationResult;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketRepository;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CheckInService {

    private final EventManagementPort events;
    private final CurrentActor actor;
    private final TicketService tickets;
    private final TicketRepository repository;

    public CheckInService(EventManagementPort events, CurrentActor actor, TicketService tickets, TicketRepository repository) {
        this.events = Objects.requireNonNull(events);
        this.actor = Objects.requireNonNull(actor);
        this.tickets = Objects.requireNonNull(tickets);
        this.repository = Objects.requireNonNull(repository);
    }

    public TicketValidationResult validate(UUID eventId, UUID ticketCode) {
        authorize(eventId);
        return tickets.validate(ticketCode, eventId);
    }

    @Transactional
    public TicketView checkIn(UUID eventId, UUID ticketCode) {
        authorize(eventId);
        return tickets.checkIn(new CheckInTicketCommand(ticketCode, eventId));
    }

    @Transactional
    public TicketView reverse(UUID eventId, UUID ticketCode) {
        authorize(eventId);
        return tickets.reverseCheckIn(new ReverseCheckInCommand(ticketCode, eventId));
    }

    public List<TicketView> search(UUID eventId, String attendeeEmail) {
        authorize(eventId);
        if (attendeeEmail == null || attendeeEmail.trim().length() < 3) {
            throw new IllegalArgumentException("Search requires at least three characters");
        }
        String needle = attendeeEmail.trim().toLowerCase(Locale.ROOT);
        return repository
            .findAllByEventId(eventId)
            .stream()
            .filter(ticket -> ticket.getAttendeeEmail().value().toLowerCase(Locale.ROOT).contains(needle))
            .limit(50)
            .map(ticket -> tickets.get(ticket.getId().value()))
            .toList();
    }

    public CheckInCounts counts(UUID eventId) {
        authorize(eventId);
        var eventTickets = repository.findAllByEventId(eventId);
        long registered = eventTickets
            .stream()
            .filter(ticket -> ticket.getStatus() == TicketStatus.VALID || ticket.getStatus() == TicketStatus.CHECKED_IN)
            .count();
        long checkedIn = eventTickets
            .stream()
            .filter(ticket -> ticket.getStatus() == TicketStatus.CHECKED_IN)
            .count();
        return new CheckInCounts(registered, checkedIn);
    }

    private void authorize(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        if (!events.canCheckIn(eventId, actor.requireUserId())) {
            throw new AuthorizationException("User is not assigned to check in attendees for this event");
        }
    }

    public record CheckInCounts(long registered, long checkedIn) {}
}
