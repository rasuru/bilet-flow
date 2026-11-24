package com.biletflow.biletflow.ticketinventory.application.ticket;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.ticketinventory.application.audit.TicketInventoryAuditRepository.TicketInventoryAuditType;
import com.biletflow.biletflow.ticketinventory.application.audit.TicketInventoryAuditService;
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
    private final TicketInventoryAuditService audit;

    public CheckInService(
        EventManagementPort events,
        CurrentActor actor,
        TicketService tickets,
        TicketRepository repository,
        TicketInventoryAuditService audit
    ) {
        this.events = Objects.requireNonNull(events);
        this.actor = Objects.requireNonNull(actor);
        this.tickets = Objects.requireNonNull(tickets);
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    public TicketValidationResult validate(UUID eventId, UUID ticketCode) {
        authorize(eventId);
        return tickets.validate(ticketCode, eventId);
    }

    @Transactional
    public TicketView checkIn(UUID eventId, UUID ticketCode) {
        Long staffUserId = authorize(eventId);
        TicketView ticket = tickets.checkIn(new CheckInTicketCommand(ticketCode, eventId));
        audit.record(eventId, staffUserId, TicketInventoryAuditType.TICKET_CHECKED_IN, ticket.id(), "Ticket checked in");
        return ticket;
    }

    @Transactional
    public TicketView reverse(UUID eventId, UUID ticketCode) {
        Long staffUserId = authorize(eventId);
        TicketView ticket = tickets.reverseCheckIn(new ReverseCheckInCommand(ticketCode, eventId));
        audit.record(eventId, staffUserId, TicketInventoryAuditType.CHECK_IN_REVERSED, ticket.id(), "Check-in reversed");
        return ticket;
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

    private Long authorize(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Long userId = actor.requireUserId();
        if (!events.canCheckIn(eventId, userId)) {
            throw new AuthorizationException("User is not assigned to check in attendees for this event");
        }
        return userId;
    }

    public record CheckInCounts(long registered, long checkedIn) {}
}
