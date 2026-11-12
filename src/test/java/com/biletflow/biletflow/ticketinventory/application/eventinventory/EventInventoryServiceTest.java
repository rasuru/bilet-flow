package com.biletflow.biletflow.ticketinventory.application.eventinventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.AssignedSeatsSelection;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.HoldInventoryCommand;
import com.biletflow.biletflow.ticketinventory.domain.common.HoldExpiry;
import com.biletflow.biletflow.ticketinventory.domain.common.SeatId;
import com.biletflow.biletflow.ticketinventory.domain.common.SessionId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.AssignedSeatingInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventoryRepository;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.SalesWindow;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketType;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import com.biletflow.biletflow.ticketinventory.ohs.TicketSalesAvailability;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventInventoryServiceTest {

    @Test
    void wrongPriceCategoryCannotHoldSeat() {
        UUID eventId = UUID.randomUUID();
        UUID rawTicketTypeId = UUID.randomUUID();
        UUID rawSeatId = UUID.randomUUID();

        SeatId seatId = new SeatId(rawSeatId);

        EventInventory inventory = EventInventory.create(
            eventId,
            HoldExpiry.defaultExpiry(),
            AssignedSeatingInventory.create(Map.of(seatId, "VIP"))
        );

        EventInventoryRepository inventoryRepository = mock(EventInventoryRepository.class);

        TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);

        TicketType ticketType = mock(TicketType.class);

        when(inventoryRepository.findByEventId(eventId)).thenReturn(Optional.of(inventory));

        when(ticketTypeRepository.findById(new TicketTypeId(rawTicketTypeId))).thenReturn(Optional.of(ticketType));

        when(ticketType.getId()).thenReturn(new TicketTypeId(rawTicketTypeId));

        when(ticketType.getEventId()).thenReturn(eventId);

        when(ticketType.isPurchasableAt(any())).thenReturn(true);

        when(ticketType.getMaxPerOrder()).thenReturn(5);

        when(ticketType.getPriceCategory()).thenReturn("STANDARD");

        Clock clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);

        EventInventoryService service = new EventInventoryService(inventoryRepository, ticketTypeRepository, clock);

        HoldInventoryCommand command = new HoldInventoryCommand(
            eventId,
            rawTicketTypeId,
            "session-a",
            new AssignedSeatsSelection(Set.of(rawSeatId))
        );

        assertThrows(IllegalStateException.class, () -> service.hold(command));

        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void paidAssignedSeatingRequiresActivationAndAnAvailableSeatInTheRightCategory() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-03T12:00:00Z");
        SeatId vipSeat = new SeatId(UUID.randomUUID());
        SeatId standardSeat = new SeatId(UUID.randomUUID());
        TicketType ticketType = TicketType.createAssignedSeating(
            eventId,
            "VIP",
            "VIP admission",
            Money.kzt(1000),
            1,
            new SalesWindow(now.minusSeconds(3600), now.plusSeconds(3600)),
            "VIP"
        );
        EventInventory inventory = EventInventory.create(
            eventId,
            HoldExpiry.defaultExpiry(),
            AssignedSeatingInventory.create(Map.of(vipSeat, "VIP", standardSeat, "STANDARD"))
        );
        var hold = inventory.holdSeat(ticketType.getId(), vipSeat, new SessionId("session-a"), now);
        EventInventoryRepository inventoryRepository = mock(EventInventoryRepository.class);
        TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);
        when(inventoryRepository.findByEventId(eventId)).thenReturn(Optional.of(inventory));
        when(ticketTypeRepository.findAllByEventId(eventId)).thenReturn(List.of(ticketType));
        EventInventoryService service = new EventInventoryService(
            inventoryRepository,
            ticketTypeRepository,
            Clock.fixed(now, ZoneOffset.UTC)
        );

        assertEquals(TicketSalesAvailability.UNAVAILABLE, service.salesAvailability(eventId, now, false));
        assertEquals(TicketSalesAvailability.SOLD_OUT, service.salesAvailability(eventId, now, true));
        assertEquals(TicketSalesAvailability.AVAILABLE, service.salesAvailability(eventId, hold.getExpiresAt(), true));
        verify(inventoryRepository, never()).save(any());
    }
}
