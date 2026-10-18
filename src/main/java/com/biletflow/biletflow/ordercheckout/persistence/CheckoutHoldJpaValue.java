package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs.Hold;
import jakarta.persistence.*;
import java.util.UUID;

@Embeddable
public class CheckoutHoldJpaValue {

    @Column(name = "reference_id", nullable = false)
    UUID referenceId;

    @Column(name = "assigned_seat", nullable = false)
    boolean assignedSeat;

    protected CheckoutHoldJpaValue() {}

    CheckoutHoldJpaValue(Hold hold) {
        referenceId = hold.referenceId();
        assignedSeat = hold.assignedSeat();
    }

    Hold domain() {
        return new Hold(referenceId, assignedSeat);
    }
}
