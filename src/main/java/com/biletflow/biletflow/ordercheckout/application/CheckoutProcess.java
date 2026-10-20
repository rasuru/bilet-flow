package com.biletflow.biletflow.ordercheckout.application;

import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutRefundStatus;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs.Hold;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs.Line;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Saga state stores references and snapshots from published OHS contracts, never foreign aggregates. */
public record CheckoutProcess(
    UUID id,
    long ownerId,
    UUID eventId,
    String fingerprint,
    Instant deadline,
    List<Line> lines,
    List<Hold> holds,
    UUID paymentId,
    CheckoutRefundStatus refundState,
    UUID refundId
) {
    public CheckoutProcess {
        lines = List.copyOf(lines);
        holds = List.copyOf(holds);
    }

    public CheckoutProcess payment(UUID id) {
        return new CheckoutProcess(this.id, ownerId, eventId, fingerprint, deadline, lines, holds, id, refundState, refundId);
    }

    public CheckoutProcess refund(CheckoutRefundStatus state, UUID id) {
        return new CheckoutProcess(this.id, ownerId, eventId, fingerprint, deadline, lines, holds, paymentId, state, id);
    }
}
