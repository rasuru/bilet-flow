package com.biletflow.biletflow.ordercheckout.persistence.checkout.entity;

import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs.Hold;
import jakarta.persistence.*;
import java.util.UUID;

@Embeddable
public class CheckoutHoldJpaEmbeddable {

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Column(name = "assigned_seat", nullable = false)
    private boolean assignedSeat;

    protected CheckoutHoldJpaEmbeddable() {}

    public CheckoutHoldJpaEmbeddable(Hold hold) {
        referenceId = hold.referenceId();
        assignedSeat = hold.assignedSeat();
    }

    public Hold toDomain() {
        return new Hold(referenceId, assignedSeat);
    }
}
