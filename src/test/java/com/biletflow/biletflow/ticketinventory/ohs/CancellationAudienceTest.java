package com.biletflow.biletflow.ticketinventory.ohs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.ticket.*;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class CancellationAudienceTest {

    @Test
    void includesUnclaimedAndCheckedInTicketsButExcludesCancelledAndRefundedTickets() {
        UUID event = UUID.randomUUID();
        var repository = mock(TicketRepository.class);
        var tickets = Arrays.stream(TicketStatus.values())
            .map(status ->
                new Ticket(
                    TicketId.generate(),
                    TicketTypeId.generate(),
                    event,
                    new OrderId(UUID.randomUUID()),
                    new AttendeeEmail(status.name().toLowerCase(Locale.ROOT) + "@example.com"),
                    null,
                    null,
                    TicketCode.generate(),
                    status,
                    Instant.now()
                )
            )
            .toList();
        when(repository.findAllByEventId(event)).thenReturn(tickets);
        assertEquals(
            Set.of("valid@example.com", "checked_in@example.com", "refund_pending@example.com"),
            new HashSet<>(new DefaultEventCancellationRecipientsOhs(repository).attendeeEmails(event))
        );
    }
}
