package com.biletflow.biletflow.ticketinventory.application.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.ticketinventory.application.audit.TicketInventoryAuditRepository.TicketInventoryAuditType;
import com.biletflow.biletflow.ticketinventory.application.audit.TicketInventoryAuditService;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventManagementPort;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import com.biletflow.biletflow.ticketinventory.domain.ticket.Ticket;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketRepository;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckInServiceTest {

    private final EventManagementPort events = mock(EventManagementPort.class);
    private final CurrentActor actor = mock(CurrentActor.class);
    private final TicketService tickets = mock(TicketService.class);
    private final TicketRepository repository = mock(TicketRepository.class);
    private final TicketInventoryAuditService audit = mock(TicketInventoryAuditService.class);
    private final CheckInService service = new CheckInService(events, actor, tickets, repository, audit);
    private final UUID eventId = UUID.randomUUID();

    @Test
    void unauthorizedStaffCannotReadTicketsOrPerformCheckIn() {
        when(actor.requireUserId()).thenReturn(7L);

        assertThatThrownBy(() -> service.search(eventId, "abc")).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> service.counts(eventId)).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> service.checkIn(eventId, UUID.randomUUID())).isInstanceOf(AuthorizationException.class);

        verifyNoInteractions(repository, tickets, audit);
    }

    @Test
    void checkInAndReversalAreRecordedInTheEventTimeline() {
        when(actor.requireUserId()).thenReturn(7L);
        when(events.canCheckIn(eventId, 7L)).thenReturn(true);
        UUID ticketCode = UUID.randomUUID();
        TicketView view = new TicketView(
            UUID.randomUUID(),
            UUID.randomUUID(),
            eventId,
            UUID.randomUUID(),
            "attendee@example.com",
            null,
            null,
            ticketCode,
            TicketStatus.CHECKED_IN,
            Instant.parse("2026-11-22T10:00:00Z")
        );
        when(tickets.checkIn(any())).thenReturn(view);
        when(tickets.reverseCheckIn(any())).thenReturn(view);

        service.checkIn(eventId, ticketCode);
        service.reverse(eventId, ticketCode);

        verify(audit).record(eventId, 7L, TicketInventoryAuditType.TICKET_CHECKED_IN, view.id(), "Ticket checked in");
        verify(audit).record(eventId, 7L, TicketInventoryAuditType.CHECK_IN_REVERSED, view.id(), "Check-in reversed");
    }

    @Test
    void failedCheckInIsNotRecorded() {
        when(actor.requireUserId()).thenReturn(7L);
        when(events.canCheckIn(eventId, 7L)).thenReturn(true);
        when(tickets.checkIn(any())).thenThrow(new IllegalStateException("Ticket already checked in"));

        assertThatThrownBy(() -> service.checkIn(eventId, UUID.randomUUID())).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(audit);
    }

    @Test
    void countsExcludeCancelledAndRefundedTickets() {
        when(actor.requireUserId()).thenReturn(7L);
        when(events.canCheckIn(eventId, 7L)).thenReturn(true);
        Ticket valid = mock(Ticket.class);
        Ticket used = mock(Ticket.class);
        Ticket cancelled = mock(Ticket.class);
        Ticket refunded = mock(Ticket.class);
        when(valid.getStatus()).thenReturn(TicketStatus.VALID);
        when(used.getStatus()).thenReturn(TicketStatus.CHECKED_IN);
        when(cancelled.getStatus()).thenReturn(TicketStatus.CANCELLED);
        when(refunded.getStatus()).thenReturn(TicketStatus.REFUNDED);
        when(repository.findAllByEventId(eventId)).thenReturn(List.of(valid, used, cancelled, refunded));

        assertThat(service.counts(eventId)).isEqualTo(new CheckInService.CheckInCounts(2, 1));
    }
}
