package com.biletflow.biletflow.eventmanagement.persistence.cancellation.entity;

import com.biletflow.biletflow.eventmanagement.application.cancellation.CancellationNotice;
import com.biletflow.biletflow.eventmanagement.application.cancellation.CancellationNoticeStore.Claim;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "event_management_cancellation_notice",
    uniqueConstraints = @UniqueConstraint(name = "uk_event_cancellation_recipient", columnNames = { "event_id", "recipient" })
)
public class CancellationNoticeJpaEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false, length = 254)
    private String recipient;

    @Column(nullable = false, updatable = false, length = 255)
    private String title;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Column(name = "cancelled_at", nullable = false, updatable = false)
    private Instant cancelledAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Version
    private long version;

    protected CancellationNoticeJpaEntity() {}

    public CancellationNoticeJpaEntity(CancellationNotice notice) {
        id = notice.id();
        eventId = notice.eventId();
        recipient = notice.recipient();
        title = notice.title();
        startsAt = notice.startsAt();
        endsAt = notice.endsAt();
        cancelledAt = notice.cancelledAt();
        nextAttemptAt = notice.cancelledAt();
    }

    public Claim claim(Instant now) {
        if (sentAt != null || nextAttemptAt.isAfter(now) || (leaseUntil != null && leaseUntil.isAfter(now))) {
            throw new IllegalStateException("Cancellation notice is not available");
        }
        claimToken = UUID.randomUUID();
        leaseUntil = now.plusSeconds(120);
        attempts = Math.min(attempts + 1, 1000000);
        return new Claim(new CancellationNotice(id, eventId, recipient, title, startsAt, endsAt, cancelledAt), claimToken);
    }

    public void complete(Claim claim, Instant now) {
        if (!owns(claim)) return;
        sentAt = now;
        claimToken = null;
        leaseUntil = null;
    }

    public void retry(Claim claim, Instant now) {
        if (!owns(claim)) return;
        nextAttemptAt = now.plusSeconds(Math.min(3600, 30L << Math.min(Math.max(0, attempts - 1), 7)));
        claimToken = null;
        leaseUntil = null;
    }

    private boolean owns(Claim claim) {
        return sentAt == null && claim.token().equals(claimToken);
    }
}
