package com.biletflow.biletflow.ticketinventory.domain.eventinventory;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.ticketinventory.domain.common.HoldExpiry;
import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.common.SessionId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryCounters;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryReservation;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.ReservationStatus;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeneralAdmissionInventoryTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void reservationsCannotExceedCapacity() {
        TicketTypeId ticketTypeId = TicketTypeId.generate();

        EventInventory inventory = EventInventory.create(
            UUID.randomUUID(),
            new HoldExpiry(Duration.ofMinutes(10)),
            GeneralAdmissionInventory.empty()
        );

        inventory.configureTicketType(ticketTypeId, 3);

        inventory.reserveGA(ticketTypeId, 2, new SessionId("session-a"), NOW);

        assertThrows(IllegalStateException.class, () -> inventory.reserveGA(ticketTypeId, 2, new SessionId("session-b"), NOW));

        InventoryCounters counters = ((GeneralAdmissionInventory) inventory.getMode()).getCounters().get(ticketTypeId);

        assertEquals(3, counters.totalCapacity());
        assertEquals(2, counters.reserved());
        assertEquals(0, counters.sold());
        assertEquals(1, counters.available());
    }

    @Test
    void expiredReservationBecomesAvailableAgain() {
        TicketTypeId ticketTypeId = TicketTypeId.generate();

        EventInventory inventory = EventInventory.create(
            UUID.randomUUID(),
            new HoldExpiry(Duration.ofMinutes(10)),
            GeneralAdmissionInventory.empty()
        );

        inventory.configureTicketType(ticketTypeId, 2);

        InventoryReservation reservation = inventory.reserveGA(ticketTypeId, 2, new SessionId("session-a"), NOW);

        inventory.cleanUpExpiredHolds(NOW.plus(Duration.ofMinutes(10)));

        InventoryCounters counters = ((GeneralAdmissionInventory) inventory.getMode()).getCounters().get(ticketTypeId);

        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());
        assertEquals(0, counters.reserved());
        assertEquals(0, counters.sold());
        assertEquals(2, counters.available());
    }

    @Test
    void confirmationMovesReservedInventoryToSold() {
        TicketTypeId ticketTypeId = TicketTypeId.generate();

        EventInventory inventory = EventInventory.create(UUID.randomUUID(), HoldExpiry.defaultExpiry(), GeneralAdmissionInventory.empty());

        inventory.configureTicketType(ticketTypeId, 5);

        InventoryReservation reservation = inventory.reserveGA(ticketTypeId, 2, new SessionId("session-a"), NOW);

        OrderId orderId = new OrderId(UUID.randomUUID());

        inventory.confirmGAReservation(reservation.getId(), orderId, NOW.plusSeconds(30));

        InventoryCounters counters = ((GeneralAdmissionInventory) inventory.getMode()).getCounters().get(ticketTypeId);

        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertEquals(orderId, reservation.getOrderId());
        assertEquals(0, counters.reserved());
        assertEquals(2, counters.sold());
        assertEquals(3, counters.available());
    }
}
