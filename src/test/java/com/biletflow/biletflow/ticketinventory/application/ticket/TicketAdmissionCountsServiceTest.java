package com.biletflow.biletflow.ticketinventory.application.ticket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventManagementPort;
import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.ticket.*;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketAdmissionCountsServiceTest {

    @Test
    void groupsRefundedAndCheckedInTicketsByTicketTypeIncludingEmptyTypes() {
        UUID eventId = UUID.randomUUID();
        TicketTypeId firstType = TicketTypeId.generate();
        TicketTypeId secondType = TicketTypeId.generate();
        EventManagementPort events = mock(EventManagementPort.class);
        TicketRepository tickets = mock(TicketRepository.class);
        TicketTypeRepository types = mock(TicketTypeRepository.class);
        CurrentActor actor = () -> Optional.of(55L);
        when(events.canManageTicketing(eventId, 55L)).thenReturn(true);
        when(tickets.findAllByEventId(eventId)).thenReturn(List.of(
            ticket(eventId, firstType, TicketStatus.REFUNDED),
            ticket(eventId, firstType, TicketStatus.CHECKED_IN),
            ticket(eventId, firstType, TicketStatus.CHECKED_IN)
        ));
        TicketType first = mock(TicketType.class);
        when(first.getId()).thenReturn(firstType);
        when(first.getName()).thenReturn("Standard");
        TicketType second = mock(TicketType.class);
        when(second.getId()).thenReturn(secondType);
        when(second.getName()).thenReturn("VIP");
        when(types.findAllByEventId(eventId)).thenReturn(List.of(first, second));

        var result = new TicketAdmissionCountsService(events, actor, tickets, types).counts(eventId);

        assertEquals(2, result.size());
        assertEquals(1, result.stream().filter(v -> v.ticketTypeId().equals(firstType.value())).findFirst().orElseThrow().refundedCount());
        assertEquals(2, result.stream().filter(v -> v.ticketTypeId().equals(firstType.value())).findFirst().orElseThrow().checkedInCount());
        assertEquals(0, result.stream().filter(v -> v.ticketTypeId().equals(secondType.value())).findFirst().orElseThrow().refundedCount());
        assertEquals(0, result.stream().filter(v -> v.ticketTypeId().equals(secondType.value())).findFirst().orElseThrow().checkedInCount());
    }

    @Test
    void deniesUsersWithoutCheckInPermission() {
        UUID eventId = UUID.randomUUID();
        EventManagementPort events = mock(EventManagementPort.class);
        CurrentActor actor = () -> Optional.of(55L);
        when(events.canManageTicketing(eventId, 55L)).thenReturn(false);

        assertThrows(AuthorizationException.class, () -> new TicketAdmissionCountsService(
            events, actor, mock(TicketRepository.class), mock(TicketTypeRepository.class)
        ).counts(eventId));
    }

    private Ticket ticket(UUID eventId, TicketTypeId typeId, TicketStatus status) {
        return new Ticket(TicketId.generate(), typeId, eventId, new OrderId(UUID.randomUUID()),
            new AttendeeEmail("attendee@example.com"), 55L, null, TicketCode.generate(), status, Instant.parse("2026-11-20T12:00:00Z"));
    }
}
