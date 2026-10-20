package com.biletflow.biletflow.ticketinventory.persistence.delivery.entity;

import com.biletflow.biletflow.ticketinventory.application.delivery.TicketDeliveryCooldownException;
import com.biletflow.biletflow.ticketinventory.application.delivery.TicketDeliveryStore.Claim;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_inventory_delivery")
public class TicketDeliveryJpaEntity {

    public enum State {
        PENDING,
        SENT,
        SKIPPED,
    }

    @Id
    @Column(name = "ticket_id", updatable = false)
    private UUID ticketId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private State state;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    protected TicketDeliveryJpaEntity() {}

    public TicketDeliveryJpaEntity(UUID ticketId, Instant now) {
        this.ticketId = ticketId;
        reset(now);
    }

    private void reset(Instant now) {
        state = State.PENDING;
        messageId = UUID.randomUUID();
        attempts = 0;
        nextAttemptAt = now;
        requestedAt = now;
        claimToken = null;
        leaseUntil = null;
        completedAt = null;
    }

    public void request(Instant now) {
        if (state == State.PENDING) return;
        if (now.isBefore(requestedAt.plusSeconds(60))) throw new TicketDeliveryCooldownException();
        reset(now);
    }

    public Claim claim(Instant now) {
        if (state != State.PENDING || nextAttemptAt.isAfter(now) || (leaseUntil != null && leaseUntil.isAfter(now))) {
            throw new IllegalStateException("Ticket delivery is not available");
        }
        claimToken = UUID.randomUUID();
        leaseUntil = now.plusSeconds(120);
        attempts++;
        return new Claim(ticketId, claimToken, messageId);
    }

    private boolean owns(Claim claim) {
        return state == State.PENDING && claim.token().equals(claimToken);
    }

    public void complete(Claim claim, boolean skipped, Instant now) {
        if (!owns(claim)) return;
        state = skipped ? State.SKIPPED : State.SENT;
        completedAt = now;
        leaseUntil = null;
        claimToken = null;
    }

    public void retry(Claim claim, Instant now) {
        if (!owns(claim)) return;
        nextAttemptAt = now.plusSeconds(Math.min(3600, 30L << Math.min(Math.max(0, attempts - 1), 7)));
        leaseUntil = null;
        claimToken = null;
    }
}
