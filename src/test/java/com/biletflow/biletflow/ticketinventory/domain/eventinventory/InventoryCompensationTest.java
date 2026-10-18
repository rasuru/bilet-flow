package com.biletflow.biletflow.ticketinventory.domain.eventinventory;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.ticketinventory.domain.common.*;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class InventoryCompensationTest {

    @Test
    void gaReleaseAfterLazyExpiryDoesNotReleaseAnotherBuyersReservation() {
        var stock = GeneralAdmissionInventory.empty();
        var type = TicketTypeId.generate();
        stock.configureTicketType(type, 2);
        var now = Instant.parse("2026-10-18T12:00:00Z");
        var owner = new SessionId("first");
        var old = stock.reserve(type, 1, owner, HoldExpiry.defaultExpiry(), now);
        stock.reserve(type, 1, new SessionId("second"), HoldExpiry.defaultExpiry(), now.plusSeconds(601));
        stock.releaseReservation(old.getId(), owner);
        stock.releaseReservation(old.getId(), owner);
        assertEquals(1, stock.getCounters().get(type).reserved());
        assertEquals(1, stock.getCounters().get(type).available());
        assertThrows(IllegalStateException.class, () -> stock.releaseReservation(old.getId(), new SessionId("other")));
    }

    @Test
    void seatReleaseAfterExpiryDoesNotReleaseTheNewHold() {
        var seat = new SeatId(UUID.randomUUID());
        var stock = AssignedSeatingInventory.create(Map.of(seat, "STANDARD"));
        var type = TicketTypeId.generate();
        var now = Instant.parse("2026-10-18T12:00:00Z");
        var owner = new SessionId("first");
        var old = stock.holdSeat(type, seat, owner, HoldExpiry.defaultExpiry(), now);
        var next = stock.holdSeat(type, seat, new SessionId("second"), HoldExpiry.defaultExpiry(), now.plusSeconds(601));
        stock.releaseHold(old.getId(), owner);
        stock.releaseHold(old.getId(), owner);
        assertEquals(com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldStatus.HELD, next.getStatus());
        assertThrows(IllegalStateException.class, () -> stock.releaseHold(old.getId(), new SessionId("other")));
    }
}
