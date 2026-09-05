package com.biletflow.biletflow.ticketinventory.application.sale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.GaReservationReference;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.SeatHoldReference;
import com.biletflow.biletflow.ticketinventory.application.sale.command.CompleteSaleCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.IssueTicketItem;
import com.biletflow.biletflow.ticketinventory.domain.common.HoldExpiry;
import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.common.SeatId;
import com.biletflow.biletflow.ticketinventory.domain.common.SessionId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.AssignedSeatingInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventoryRepository;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.GeneralAdmissionInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHold;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldStatus;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryCounters;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryReservation;
import com.biletflow.biletflow.ticketinventory.domain.ticket.Ticket;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketRepository;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketType;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;

class TicketSaleServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void completeSaleMovesGaReservedToSoldAndIssuesExactQuantity() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        TicketTypeId ticketTypeId = TicketTypeId.generate();

        EventInventory inventory = EventInventory.create(eventId, HoldExpiry.defaultExpiry(), GeneralAdmissionInventory.empty());

        inventory.configureTicketType(ticketTypeId, 5);

        InventoryReservation reservation = inventory.reserveGA(ticketTypeId, 2, new SessionId("session-a"), NOW);

        Fixture fixture = fixture(inventory, ticketTypeId, eventId);

        when(fixture.ticketRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        CompleteSaleCommand command = new CompleteSaleCommand(
            orderId,
            eventId,
            List.of(new GaReservationReference(reservation.getId().value())),
            List.of(
                new IssueTicketItem(ticketTypeId.value(), "one@example.com", null, null),
                new IssueTicketItem(ticketTypeId.value(), "two@example.com", null, null)
            )
        );

        var result = fixture.service.complete(command);

        InventoryCounters counters = ((GeneralAdmissionInventory) inventory.getMode()).getCounters().get(ticketTypeId);

        assertEquals(2, result.size());
        assertEquals(0, counters.reserved());
        assertEquals(2, counters.sold());

        verify(fixture.inventoryRepository).save(inventory);

        verify(fixture.ticketRepository).saveAll(
            argThat(tickets -> tickets.size() == 2 && tickets.stream().allMatch(ticket -> ticket.getOrderId().value().equals(orderId)))
        );
    }

    @Test
    void repeatedCompleteSaleForSameOrderIsIdempotent() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        TicketTypeId ticketTypeId = TicketTypeId.generate();

        EventInventory inventory = EventInventory.create(eventId, HoldExpiry.defaultExpiry(), GeneralAdmissionInventory.empty());

        inventory.configureTicketType(ticketTypeId, 1);

        InventoryReservation reservation = inventory.reserveGA(ticketTypeId, 1, new SessionId("session-a"), NOW);

        Fixture fixture = fixture(inventory, ticketTypeId, eventId);

        List<Ticket> stored = new ArrayList<>();

        when(fixture.ticketRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Ticket> tickets = invocation.getArgument(0);
            stored.clear();
            stored.addAll(tickets);
            return tickets;
        });

        when(fixture.ticketRepository.findAllByOrderId(new OrderId(orderId))).thenAnswer(ignored -> List.copyOf(stored));

        CompleteSaleCommand command = new CompleteSaleCommand(
            orderId,
            eventId,
            List.of(new GaReservationReference(reservation.getId().value())),
            List.of(new IssueTicketItem(ticketTypeId.value(), "buyer@example.com", null, null))
        );

        var first = fixture.service.complete(command);
        var second = fixture.service.complete(command);

        assertEquals(1, first.size());
        assertEquals(first, second);

        verify(fixture.inventoryRepository, times(1)).save(inventory);

        verify(fixture.ticketRepository, times(1)).saveAll(anyList());
    }

    @Test
    void assignedSaleMustIssueExactlyHeldSeats() {
        UUID eventId = UUID.randomUUID();
        TicketTypeId ticketTypeId = TicketTypeId.generate();

        SeatId seatA = new SeatId(UUID.randomUUID());

        SeatId seatB = new SeatId(UUID.randomUUID());

        EventInventory inventory = EventInventory.create(
            eventId,
            HoldExpiry.defaultExpiry(),
            AssignedSeatingInventory.create(Map.of(seatA, "VIP", seatB, "VIP"))
        );

        SeatHold holdA = inventory.holdSeat(ticketTypeId, seatA, new SessionId("session-a"), NOW);

        SeatHold holdB = inventory.holdSeat(ticketTypeId, seatB, new SessionId("session-a"), NOW);

        Fixture fixture = fixture(inventory, ticketTypeId, eventId);

        CompleteSaleCommand mismatched = new CompleteSaleCommand(
            UUID.randomUUID(),
            eventId,
            List.of(new SeatHoldReference(holdA.getId().value()), new SeatHoldReference(holdB.getId().value())),
            List.of(new IssueTicketItem(ticketTypeId.value(), "one@example.com", null, seatA.value()))
        );

        assertThrows(IllegalStateException.class, () -> fixture.service.complete(mismatched));

        assertEquals(SeatHoldStatus.HELD, holdA.getStatus());
        assertEquals(SeatHoldStatus.HELD, holdB.getStatus());

        verify(fixture.inventoryRepository, never()).save(any());

        verify(fixture.ticketRepository, never()).saveAll(anyList());
    }

    @Test
    void assignedCompleteSaleMovesAllHeldSeatsToSold() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        TicketTypeId ticketTypeId = TicketTypeId.generate();

        SeatId seatA = new SeatId(UUID.randomUUID());

        SeatId seatB = new SeatId(UUID.randomUUID());

        EventInventory inventory = EventInventory.create(
            eventId,
            HoldExpiry.defaultExpiry(),
            AssignedSeatingInventory.create(Map.of(seatA, "VIP", seatB, "VIP"))
        );

        SeatHold holdA = inventory.holdSeat(ticketTypeId, seatA, new SessionId("session-a"), NOW);

        SeatHold holdB = inventory.holdSeat(ticketTypeId, seatB, new SessionId("session-a"), NOW);

        Fixture fixture = fixture(inventory, ticketTypeId, eventId);

        when(fixture.ticketRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        CompleteSaleCommand command = new CompleteSaleCommand(
            orderId,
            eventId,
            List.of(new SeatHoldReference(holdA.getId().value()), new SeatHoldReference(holdB.getId().value())),
            List.of(
                new IssueTicketItem(ticketTypeId.value(), "one@example.com", null, seatA.value()),
                new IssueTicketItem(ticketTypeId.value(), "two@example.com", null, seatB.value())
            )
        );

        var result = fixture.service.complete(command);

        assertEquals(2, result.size());
        assertEquals(SeatHoldStatus.SOLD, holdA.getStatus());
        assertEquals(SeatHoldStatus.SOLD, holdB.getStatus());
        assertEquals(new OrderId(orderId), holdA.getOrderId());
        assertEquals(new OrderId(orderId), holdB.getOrderId());
    }

    private Fixture fixture(EventInventory inventory, TicketTypeId ticketTypeId, UUID eventId) {
        EventInventoryRepository inventoryRepository = mock(EventInventoryRepository.class);

        TicketRepository ticketRepository = mock(TicketRepository.class);

        TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);

        TicketType ticketType = mock(TicketType.class);

        when(inventoryRepository.findByEventId(eventId)).thenReturn(Optional.of(inventory));

        when(ticketRepository.findAllByOrderId(any())).thenReturn(List.of());

        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));

        when(ticketType.getEventId()).thenReturn(eventId);

        TicketSaleService service = new TicketSaleService(
            inventoryRepository,
            ticketRepository,
            ticketTypeRepository,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        return new Fixture(service, inventoryRepository, ticketRepository);
    }

    private record Fixture(TicketSaleService service, EventInventoryRepository inventoryRepository, TicketRepository ticketRepository) {}
}
