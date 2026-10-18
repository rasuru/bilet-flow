package com.biletflow.biletflow.ticketinventory.ohs;

import com.biletflow.biletflow.common.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CheckoutInventoryOhs {
    record Selection(UUID ticketTypeId, List<UUID> seats, List<String> attendeeEmails) {
        public Selection {
            seats = List.copyOf(seats);
            attendeeEmails = List.copyOf(attendeeEmails);
        }
    }

    record Line(UUID ticketTypeId, UUID seatId, Money price, String email) {}

    record Hold(UUID referenceId, boolean assignedSeat) {}

    record Reservation(List<Line> lines, List<Hold> holds, Instant expiresAt, boolean assignedSeating) {}

    record IssuedTicket(UUID id, UUID code, String status, UUID ticketTypeId, UUID seatId, String attendeeEmail) {}

    Reservation reserve(UUID eventId, UUID checkoutId, List<Selection> selections);
    void release(UUID eventId, UUID checkoutId, List<Hold> holds);
    List<IssuedTicket> issue(UUID eventId, UUID checkoutId, long ownerId, List<Line> lines, List<Hold> holds);
    List<IssuedTicket> tickets(UUID orderId);
    void prepareRefund(UUID eventId, UUID orderId);
    void completeRefund(UUID eventId, UUID orderId);
    void failRefund(UUID eventId, UUID orderId);
    void cancelFreeOrder(UUID eventId, UUID orderId);
}
