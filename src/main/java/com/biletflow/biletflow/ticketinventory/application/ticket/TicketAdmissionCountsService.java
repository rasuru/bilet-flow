package com.biletflow.biletflow.ticketinventory.application.ticket;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketTypeAdmissionCountsView;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketRepository;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventManagementPort;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TicketAdmissionCountsService {

    private final EventManagementPort events;
    private final CurrentActor actor;
    private final TicketRepository tickets;
    private final TicketTypeRepository ticketTypes;

    public TicketAdmissionCountsService(
        EventManagementPort events,
        CurrentActor actor,
        TicketRepository tickets,
        TicketTypeRepository ticketTypes
    ) {
        this.events = Objects.requireNonNull(events);
        this.actor = Objects.requireNonNull(actor);
        this.tickets = Objects.requireNonNull(tickets);
        this.ticketTypes = Objects.requireNonNull(ticketTypes);
    }

    public List<TicketTypeAdmissionCountsView> counts(UUID eventId) {
        Long userId = actor.requireUserId();
        if (!events.canManageTicketing(eventId, userId)) {
            throw new AuthorizationException("Only an authorized event manager can view ticket counts");
        }

        Map<UUID, long[]> counts = new HashMap<>();
        for (var ticket : tickets.findAllByEventId(eventId)) {
            long[] values = counts.computeIfAbsent(ticket.getTicketTypeId().value(), ignored -> new long[2]);
            if (ticket.getStatus() == TicketStatus.REFUNDED) values[0]++;
            if (ticket.getStatus() == TicketStatus.CHECKED_IN) values[1]++;
        }

        return ticketTypes.findAllByEventId(eventId).stream()
            .sorted(Comparator.comparing(type -> type.getName().toLowerCase(Locale.ROOT)))
            .map(type -> {
                long[] values = counts.getOrDefault(type.getId().value(), new long[2]);
                return new TicketTypeAdmissionCountsView(type.getId().value(), type.getName(), values[0], values[1]);
            })
            .toList();
    }
}
