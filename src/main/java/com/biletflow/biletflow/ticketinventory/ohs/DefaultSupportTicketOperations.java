package com.biletflow.biletflow.ticketinventory.ohs;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ticketinventory.application.delivery.TicketDocumentService;
import com.biletflow.biletflow.ticketinventory.application.ticket.TicketService;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.ReverseCheckInCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultSupportTicketOperations implements SupportTicketOperations {
    private final TicketService tickets;
    private final TicketDocumentService documents;
    private final EventManagementOhs events;
    private final CurrentActor actor;

    public DefaultSupportTicketOperations(TicketService tickets, TicketDocumentService documents,
                                          EventManagementOhs events, CurrentActor actor) {
        this.tickets = tickets;
        this.documents = documents;
        this.events = events;
        this.actor = actor;
    }

    @Override
    public void resend(UUID eventId, UUID orderId, UUID ticketId) {
        long user = actor.requireUserId();
        if (!actor.isAdministrator() && !events.canHandleAttendeeSupport(eventId, user)) {
            throw new AuthorizationException("Cannot resend tickets for this event");
        }
        requireTicket(eventId, orderId, ticketId);
        documents.requestSupportEmail(ticketId);
    }

    @Override
    public void reverseCheckIn(UUID eventId, UUID orderId, UUID ticketId) {
        long user = actor.requireUserId();
        if (!actor.isAdministrator() && !events.canCheckIn(eventId, user)) {
            throw new AuthorizationException("Cannot reverse check-ins for this event");
        }
        var ticket = requireTicket(eventId, orderId, ticketId);
        tickets.reverseCheckIn(new ReverseCheckInCommand(ticket.ticketCode(), eventId));
    }

    private TicketView requireTicket(UUID eventId, UUID orderId, UUID ticketId) {
        var ticket = tickets.get(ticketId);
        if (!eventId.equals(ticket.eventId()) || !orderId.equals(ticket.orderId())) {
            throw new AuthorizationException("Ticket does not belong to the linked order and event");
        }
        return ticket;
    }
}

