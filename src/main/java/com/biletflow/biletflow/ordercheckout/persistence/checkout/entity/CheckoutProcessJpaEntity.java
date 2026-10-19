package com.biletflow.biletflow.ordercheckout.persistence.checkout.entity;

import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutRefundStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "oc_saga")
public class CheckoutProcessJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "owner_id", nullable = false)
    private long ownerId;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "refund_state", length = 30)
    @Enumerated(EnumType.STRING)
    private CheckoutRefundStatus refundState;

    @Column(name = "refund_id")
    private UUID refundId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ElementCollection
    @CollectionTable(name = "oc_saga_line", joinColumns = @JoinColumn(name = "checkout_id"))
    @OrderColumn(name = "line_index")
    private List<CheckoutLineJpaEmbeddable> lines = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "oc_saga_hold", joinColumns = @JoinColumn(name = "checkout_id"))
    @OrderColumn(name = "hold_index")
    private List<CheckoutHoldJpaEmbeddable> holds = new ArrayList<>();

    protected CheckoutProcessJpaEntity() {}

    public CheckoutProcessJpaEntity(
        UUID id,
        UUID eventId,
        long ownerId,
        String fingerprint,
        Instant deadline,
        UUID paymentId,
        CheckoutRefundStatus refundState,
        UUID refundId,
        List<CheckoutLineJpaEmbeddable> lines,
        List<CheckoutHoldJpaEmbeddable> holds
    ) {
        this.id = id;
        this.eventId = eventId;
        this.ownerId = ownerId;
        this.fingerprint = fingerprint;
        this.deadline = deadline;
        this.paymentId = paymentId;
        this.refundState = refundState;
        this.refundId = refundId;
        this.lines.addAll(lines);
        this.holds.addAll(holds);
    }

    public void updateMutableState(UUID paymentId, CheckoutRefundStatus refundState, UUID refundId) {
        this.paymentId = paymentId;
        this.refundState = refundState;
        this.refundId = refundId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public CheckoutRefundStatus getRefundState() {
        return refundState;
    }

    public UUID getRefundId() {
        return refundId;
    }

    public long getVersion() {
        return version;
    }

    public List<CheckoutLineJpaEmbeddable> getLines() {
        return List.copyOf(lines);
    }

    public List<CheckoutHoldJpaEmbeddable> getHolds() {
        return List.copyOf(holds);
    }
}
