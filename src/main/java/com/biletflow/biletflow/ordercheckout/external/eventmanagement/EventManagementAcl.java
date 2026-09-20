package com.biletflow.biletflow.ordercheckout.external.eventmanagement;

import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ordercheckout.application.common.*;
import com.biletflow.biletflow.ordercheckout.application.eventmanagement.port.*;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EventManagementAcl implements EventManagementPort {

    private final EventManagementOhs eventManagementOhs;

    public EventManagementAcl(EventManagementOhs eventManagementOhs) {
        this.eventManagementOhs = Objects.requireNonNull(eventManagementOhs);
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
    public boolean canManageTicketing(UUID eventId, Long userId) {
        return eventManagementOhs.canManageTicketing(eventId, userId);
    }
}
