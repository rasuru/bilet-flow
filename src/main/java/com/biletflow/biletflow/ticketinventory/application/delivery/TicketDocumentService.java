package com.biletflow.biletflow.ticketinventory.application.delivery;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventManagementPort;
import com.biletflow.biletflow.ticketinventory.application.ticket.port.VerifiedUserEmailPort;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import com.biletflow.biletflow.ticketinventory.domain.ticket.*;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TicketDocumentService {

    private final TicketRepository tickets;
    private final TicketTypeRepository types;
    private final EventManagementPort events;
    private final VerifiedUserEmailPort emails;
    private final CurrentActor actor;
    private final TicketDocumentRenderer renderer;
    private final TicketDeliveryStore deliveries;
    private final Clock clock;

    public TicketDocumentService(
        TicketRepository tickets,
        TicketTypeRepository types,
        EventManagementPort events,
        VerifiedUserEmailPort emails,
        CurrentActor actor,
        TicketDocumentRenderer renderer,
        TicketDeliveryStore deliveries,
        Clock clock
    ) {
        this.tickets = Objects.requireNonNull(tickets);
        this.types = Objects.requireNonNull(types);
        this.events = Objects.requireNonNull(events);
        this.emails = Objects.requireNonNull(emails);
        this.actor = Objects.requireNonNull(actor);
        this.renderer = Objects.requireNonNull(renderer);
        this.deliveries = Objects.requireNonNull(deliveries);
        this.clock = Objects.requireNonNull(clock);
    }

    public byte[] pdf(UUID ticketId) {
        return renderer.pdf(document(authorize(ticketId)));
    }

    public byte[] qr(UUID ticketId) {
        return renderer.qr(document(authorize(ticketId)));
    }

    @Transactional
    public void requestEmail(UUID ticketId) {
        var ticket = authorize(ticketId);
        var document = document(ticket);
        if (ticket.getStatus() != TicketStatus.VALID || document.event().cancelled()) {
            throw new IllegalStateException("Only a valid ticket for a non-cancelled event can be emailed");
        }
        deliveries.request(ticketId, clock.instant());
    }

    /** Internal worker query; no public endpoint delegates directly to this method. */
    public TicketDocument forDelivery(UUID ticketId) {
        return document(requireTicket(ticketId));
    }

    private Ticket authorize(UUID ticketId) {
        Long user = actor.requireUserId();
        var ticket = requireTicket(ticketId);
        boolean recipient = emails
            .findVerifiedEmail(user)
            .map(email -> new AttendeeEmail(email).equals(ticket.getAttendeeEmail()))
            .orElse(false);
        if (!user.equals(ticket.getOwnerUserId()) && !recipient && !events.canManageTicketing(ticket.getEventId(), user)) {
            throw new AuthorizationException("Ticket is private");
        }
        return ticket;
    }

    private Ticket requireTicket(UUID id) {
        return tickets.findById(new TicketId(id)).orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + id));
    }

    private TicketDocument document(Ticket ticket) {
        var type = types.findById(ticket.getTicketTypeId()).orElseThrow(() -> new EntityNotFoundException("Ticket type not found"));
        var view = new TicketView(
            ticket.getId().value(),
            ticket.getTicketTypeId().value(),
            ticket.getEventId(),
            ticket.getOrderId().value(),
            ticket.getAttendeeEmail().value(),
            ticket.getOwnerUserId(),
            ticket.getSeatId() == null ? null : ticket.getSeatId().value(),
            ticket.getTicketCode().value(),
            ticket.getStatus(),
            ticket.getIssuedAt()
        );
        return new TicketDocument(view, type.getName(), events.ticketDetails(ticket.getEventId(), view.seatId()));
    }
}
