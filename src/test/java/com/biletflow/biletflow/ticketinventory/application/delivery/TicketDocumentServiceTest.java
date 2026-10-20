package com.biletflow.biletflow.ticketinventory.application.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.*;
import com.biletflow.biletflow.ticketinventory.application.ticket.port.VerifiedUserEmailPort;
import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.ticket.*;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TicketDocumentServiceTest {

    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketTypeRepository types = mock(TicketTypeRepository.class);
    private final EventManagementPort events = mock(EventManagementPort.class);
    private final VerifiedUserEmailPort emails = mock(VerifiedUserEmailPort.class);
    private final TicketDocumentRenderer renderer = mock(TicketDocumentRenderer.class);
    private final TicketDeliveryStore deliveries = mock(TicketDeliveryStore.class);
    private final Instant now = Instant.parse("2026-10-19T10:00:00Z");
    private final Ticket ticket = Ticket.issue(
        TicketTypeId.generate(),
        UUID.randomUUID(),
        new OrderId(UUID.randomUUID()),
        new AttendeeEmail("recipient@example.com"),
        null,
        null,
        now
    );
    private final TicketDocumentService service = new TicketDocumentService(
        tickets,
        types,
        events,
        emails,
        () -> Optional.of(1L),
        renderer,
        deliveries,
        Clock.fixed(now, ZoneOffset.UTC)
    );

    TicketDocumentServiceTest() {
        when(tickets.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        var type = mock(TicketType.class);
        when(type.getName()).thenReturn("General admission");
        when(types.findById(ticket.getTicketTypeId())).thenReturn(Optional.of(type));
        when(events.ticketDetails(ticket.getEventId(), null)).thenReturn(
            new TicketEventDetails("Event", now, now.plusSeconds(3600), "Venue", "Address", "General admission", false)
        );
    }

    @Test
    void verifiedRecipientCanDownloadBeforeClaimingTicket() {
        when(emails.findVerifiedEmail(1L)).thenReturn(Optional.of("RECIPIENT@example.com"));
        when(renderer.pdf(any())).thenReturn(new byte[] { 1, 2 });
        assertArrayEquals(new byte[] { 1, 2 }, service.pdf(ticket.getId().value()));
        service.requestEmail(ticket.getId().value());
        verify(deliveries).request(ticket.getId().value(), now);
    }

    @Test
    void linkedOwnerCanRead() {
        ticket.linkOwner(1L);
        service.qr(ticket.getId().value());
        verify(renderer).qr(argThat(document -> document.ticket().ticketCode().equals(ticket.getTicketCode().value())));
    }

    @Test
    void organizerCanReadTicketsWithoutTakingOwnership() {
        when(events.canManageTicketing(ticket.getEventId(), 1L)).thenReturn(true);
        service.pdf(ticket.getId().value());
        verify(renderer).pdf(any());
    }

    @Test
    void anotherUserCannotDownloadOrRequestEmail() {
        when(emails.findVerifiedEmail(1L)).thenReturn(Optional.of("someone-else@example.com"));
        assertThrows(AuthorizationException.class, () -> service.pdf(ticket.getId().value()));
        assertThrows(AuthorizationException.class, () -> service.qr(ticket.getId().value()));
        assertThrows(AuthorizationException.class, () -> service.requestEmail(ticket.getId().value()));
        verifyNoInteractions(renderer, deliveries);
        verify(events, never()).ticketDetails(any(), any());
    }

    @Test
    void refundPendingOrCancelledEventCannotTriggerDelivery() {
        ticket.linkOwner(1L);
        ticket.prepareRefund();
        assertThrows(IllegalStateException.class, () -> service.requestEmail(ticket.getId().value()));
        verifyNoInteractions(deliveries);
    }

    @Test
    void cancelledEventCannotTriggerDelivery() {
        ticket.linkOwner(1L);
        when(events.ticketDetails(ticket.getEventId(), null)).thenReturn(
            new TicketEventDetails("Event", now, now.plusSeconds(3600), "Venue", "Address", "General admission", true)
        );
        assertThrows(IllegalStateException.class, () -> service.requestEmail(ticket.getId().value()));
        verifyNoInteractions(deliveries);
    }
}
