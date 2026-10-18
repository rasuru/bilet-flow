package com.biletflow.biletflow.ordercheckout.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "oc_saga")
public class CheckoutProcessJpaEntity {

    @Id
    @Column(name = "id")
    UUID id;

    @Column(name = "event_id", nullable = false)
    UUID eventId;

    @Column(name = "owner_id", nullable = false)
    long ownerId;

    @Column(name = "fingerprint", nullable = false, length = 64)
    String fingerprint;

    @Column(name = "deadline", nullable = false)
    Instant deadline;

    @Column(name = "payment_id")
    UUID paymentId;

    @Column(name = "refund_state", length = 30)
    String refundState;

    @Column(name = "refund_id")
    UUID refundId;

    @Version
    @Column(name = "version", nullable = false)
    long version;

    @ElementCollection
    @CollectionTable(name = "oc_saga_line", joinColumns = @JoinColumn(name = "checkout_id"))
    @OrderColumn(name = "line_index")
    List<CheckoutLineJpaValue> lines = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "oc_saga_hold", joinColumns = @JoinColumn(name = "checkout_id"))
    @OrderColumn(name = "hold_index")
    List<CheckoutHoldJpaValue> holds = new ArrayList<>();

    protected CheckoutProcessJpaEntity() {}
}
