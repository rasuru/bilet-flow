package com.biletflow.biletflow.ticketinventory.domain.eventinventory;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.ticketinventory.domain.common.HoldExpiry;
import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.common.SeatId;
import com.biletflow.biletflow.ticketinventory.domain.common.SessionId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHold;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldStatus;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssignedSeatingInventoryTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void twoSessionsCannotHoldSameSeat() {
        SeatId seatId = new SeatId(UUID.randomUUID());

        EventInventory inventory = assignedInventory(seatId);

        inventory.holdSeat(TicketTypeId.generate(), seatId, new SessionId("session-a"), NOW);

        assertThrows(IllegalStateException.class, () ->
            inventory.holdSeat(TicketTypeId.generate(), seatId, new SessionId("session-b"), NOW.plusSeconds(1))
        );
    }

    @Test
    void expiredHoldFreesSeat() {
        SeatId seatId = new SeatId(UUID.randomUUID());

        TicketTypeId ticketTypeId = TicketTypeId.generate();

        EventInventory inventory = EventInventory.create(
            UUID.randomUUID(),
            new HoldExpiry(Duration.ofMinutes(10)),
            AssignedSeatingInventory.create(Map.of(seatId, "STANDARD"))
        );

        SeatHold first = inventory.holdSeat(ticketTypeId, seatId, new SessionId("session-a"), NOW);

        SeatHold second = inventory.holdSeat(ticketTypeId, seatId, new SessionId("session-b"), NOW.plus(Duration.ofMinutes(10)));

        assertEquals(SeatHoldStatus.EXPIRED, first.getStatus());
        assertEquals(SeatHoldStatus.HELD, second.getStatus());
    }

    @Test
    void confirmationMovesHeldSeatToSold() {
        SeatId seatId = new SeatId(UUID.randomUUID());

        TicketTypeId ticketTypeId = TicketTypeId.generate();

        EventInventory inventory = assignedInventory(seatId);

        SeatHold hold = inventory.holdSeat(ticketTypeId, seatId, new SessionId("session-a"), NOW);

        OrderId orderId = new OrderId(UUID.randomUUID());

        inventory.confirmSeatHold(hold.getId(), orderId, NOW.plusSeconds(30));

        assertEquals(SeatHoldStatus.SOLD, hold.getStatus());
        assertEquals(orderId, hold.getOrderId());

        assertThrows(IllegalStateException.class, () ->
            inventory.holdSeat(ticketTypeId, seatId, new SessionId("session-b"), NOW.plusSeconds(31))
        );
    }

    @Test
    void cancelledAndRefundedSoldSeatsBecomeAvailable() {
        SeatId cancelledSeat = new SeatId(UUID.randomUUID());

        SeatId refundedSeat = new SeatId(UUID.randomUUID());

        TicketTypeId ticketTypeId = TicketTypeId.generate();

        EventInventory inventory = EventInventory.create(
            UUID.randomUUID(),
            HoldExpiry.defaultExpiry(),
            AssignedSeatingInventory.create(Map.of(cancelledSeat, "STANDARD", refundedSeat, "STANDARD"))
        );

        OrderId cancelledOrder = new OrderId(UUID.randomUUID());

        SeatHold cancelledHold = inventory.holdSeat(ticketTypeId, cancelledSeat, new SessionId("cancel-session"), NOW);

        inventory.confirmSeatHold(cancelledHold.getId(), cancelledOrder, NOW.plusSeconds(10));

        inventory.cancelSoldSeat(cancelledSeat, cancelledOrder);

        assertEquals(SeatHoldStatus.CANCELLED, cancelledHold.getStatus());

        assertDoesNotThrow(() -> inventory.holdSeat(ticketTypeId, cancelledSeat, new SessionId("cancel-replacement"), NOW.plusSeconds(20)));

        OrderId refundedOrder = new OrderId(UUID.randomUUID());

        SeatHold refundedHold = inventory.holdSeat(ticketTypeId, refundedSeat, new SessionId("refund-session"), NOW);

        inventory.confirmSeatHold(refundedHold.getId(), refundedOrder, NOW.plusSeconds(10));

        inventory.refundSoldSeat(refundedSeat, refundedOrder);

        assertEquals(SeatHoldStatus.REFUNDED, refundedHold.getStatus());

        assertDoesNotThrow(() -> inventory.holdSeat(ticketTypeId, refundedSeat, new SessionId("refund-replacement"), NOW.plusSeconds(20)));
    }

    private EventInventory assignedInventory(SeatId seatId) {
        return EventInventory.create(
            UUID.randomUUID(),
            HoldExpiry.defaultExpiry(),
            AssignedSeatingInventory.create(Map.of(seatId, "STANDARD"))
        );
    }
}
