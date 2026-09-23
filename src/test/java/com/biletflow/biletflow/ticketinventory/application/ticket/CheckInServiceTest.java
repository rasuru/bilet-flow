package com.biletflow.biletflow.ticketinventory.application.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventManagementPort;
import com.biletflow.biletflow.ticketinventory.domain.ticket.Ticket;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketRepository;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckInServiceTest {

    private final EventManagementPort events = mock(EventManagementPort.class);
    private final CurrentActor actor = mock(CurrentActor.class);
    private final TicketService tickets = mock(TicketService.class);
    private final TicketRepository repository = mock(TicketRepository.class);
    private final CheckInService service = new CheckInService(events, actor, tickets, repository);
    private final UUID eventId = UUID.randomUUID();

    @Test
    void unauthorizedStaffCannotReadTicketsOrPerformCheckIn() {
        when(actor.requireUserId()).thenReturn(7L);

        assertThatThrownBy(() -> service.search(eventId, "abc")).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> service.counts(eventId)).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> service.checkIn(eventId, UUID.randomUUID())).isInstanceOf(AuthorizationException.class);

        verifyNoInteractions(repository, tickets);
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
