package com.biletflow.biletflow.ticketinventory.external.eventmanagement;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ticketinventory.application.common.InventoryKind;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventManagementPort;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventTicketingConfiguration;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.TicketEventDetails;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EventManagementAcl implements EventManagementPort {

    private final EventManagementOhs eventManagementOhs;

    private final CurrentActor actor;

    public EventManagementAcl(EventManagementOhs eventManagementOhs, CurrentActor actor) {
        this.eventManagementOhs = Objects.requireNonNull(eventManagementOhs);
        this.actor = Objects.requireNonNull(actor);
    }

    @Override
    public EventTicketingConfiguration getTicketingConfiguration(UUID eventId) {
        var upstream = eventManagementOhs.getTicketingConfiguration(eventId);

        InventoryKind kind = switch (upstream.seatingMode()) {
            case GENERAL_ADMISSION -> InventoryKind.GENERAL_ADMISSION;
            case ASSIGNED_SEATING -> InventoryKind.ASSIGNED_SEATING;
        };

        return new EventTicketingConfiguration(
            upstream.eventId(),
            kind,
            upstream
                .seats()
                .stream()
                .collect(Collectors.toUnmodifiableMap(seat -> seat.seatId(), seat -> seat.priceCategory()))
        );
    }

    @Override
    public PublicSalesScope publicSalesScope(UUID eventId, Instant now) {
        try {
            var scope = eventManagementOhs.salesScope(eventId, now);
            boolean readable = scope.publiclyReadable() || actor.userId()
                .filter(userId -> eventManagementOhs.canAccessPrivateEvent(eventId, userId))
                .isPresent();
            return new PublicSalesScope(scope.registrationOpen(), readable);
        } catch (SocialEventNotFoundException exception) {
            throw new EntityNotFoundException("Event not found: " + eventId);
        }
    }

    @Override
    public boolean canManageTicketing(UUID eventId, Long userId) {
        return eventManagementOhs.canManageTicketing(eventId, userId);
    }

    @Override
    public TicketEventDetails ticketDetails(UUID eventId, UUID seatId) {
        try {
            var details = eventManagementOhs.ticketDetails(eventId, seatId);
            return new TicketEventDetails(
                details.title(),
                details.startsAt(),
                details.endsAt(),
                details.venue(),
                details.address(),
                details.seat(),
                details.cancelled(),
                details.suspended()
            );
        } catch (SocialEventNotFoundException exception) {
            throw new EntityNotFoundException("Event not found: " + eventId);
        }
    }

    @Override
    public boolean canCheckIn(UUID eventId, Long userId) {
        try {
            return eventManagementOhs.canCheckIn(eventId, userId);
        } catch (SocialEventNotFoundException exception) {
            throw new EntityNotFoundException("Event not found: " + eventId);
        }
    }
}

