package com.biletflow.biletflow.ticketinventory.application.ticket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.*;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.*;
import com.biletflow.biletflow.ticketinventory.application.ticket.port.VerifiedUserEmailPort;
import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventoryRepository;
import com.biletflow.biletflow.ticketinventory.domain.ticket.*;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class CancelledEventAdmissionTest {

    @Test
    void internalCheckInOhsCannotBypassCancellationAndValidationReportsNoAdmission() {
        var events = mock(EventManagementPort.class);
        var repository = mock(TicketRepository.class);
        var locks = mock(TransactionLocks.class);
        var service = new TicketService(
            repository,
            mock(EventInventoryRepository.class),
            mock(VerifiedUserEmailPort.class),
            mock(CurrentActor.class),
            events,
            locks
        );
        UUID event = UUID.randomUUID();
        Instant now = Instant.now();
        var ticket = Ticket.issue(
            TicketTypeId.generate(),
            event,
            new OrderId(UUID.randomUUID()),
            new AttendeeEmail("attendee@example.com"),
            null,
            null,
            now
        );
        when(events.ticketDetails(event, null)).thenReturn(
            new TicketEventDetails("Cancelled event", now, now.plusSeconds(3600), "Venue", "Address", "General admission", true)
        );
        when(repository.findByTicketCode(ticket.getTicketCode())).thenReturn(Optional.of(ticket));
        assertFalse(service.validate(ticket.getTicketCode().value(), event).validForEntry());
        assertThrows(IllegalStateException.class, () -> service.checkIn(new CheckInTicketCommand(ticket.getTicketCode().value(), event)));
        assertThrows(IllegalStateException.class, () ->
            service.reverseCheckIn(new ReverseCheckInCommand(ticket.getTicketCode().value(), event))
        );
        verify(repository, never()).findByTicketCodeForUpdate(any());
        var ordered = inOrder(locks, events);
        ordered.verify(locks).lock("event-lifecycle:" + event);
        ordered.verify(events).ticketDetails(event, null);
    }
}
