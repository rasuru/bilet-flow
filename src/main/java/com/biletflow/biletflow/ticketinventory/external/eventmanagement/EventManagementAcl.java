package com.biletflow.biletflow.ticketinventory.external.eventmanagement;

import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ticketinventory.application.common.InventoryKind;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventManagementPort;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventTicketingConfiguration;
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
}
