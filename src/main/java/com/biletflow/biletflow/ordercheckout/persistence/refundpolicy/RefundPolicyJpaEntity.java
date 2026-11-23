package com.biletflow.biletflow.ordercheckout.persistence.refundpolicy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oc_refund_policy")
public class RefundPolicyJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(name = "refunds_allowed", nullable = false)
    private boolean refundsAllowed;

    @Column(name = "cutoff_hours_before_event", nullable = false)
    private int cutoffHoursBeforeEvent;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RefundPolicyJpaEntity() {}

    public RefundPolicyJpaEntity(UUID eventId, Long organizerId, boolean refundsAllowed, int cutoffHoursBeforeEvent, Instant updatedAt) {
        this.eventId = eventId;
        this.organizerId = organizerId;
        update(refundsAllowed, cutoffHoursBeforeEvent, updatedAt);
    }

    public void update(boolean refundsAllowed, int cutoffHoursBeforeEvent, Instant updatedAt) {
        if (cutoffHoursBeforeEvent < 0 || cutoffHoursBeforeEvent > 24 * 30) {
            throw new IllegalArgumentException("Refund cutoff must be between 0 and 720 hours");
        }
        this.refundsAllowed = refundsAllowed;
        this.cutoffHoursBeforeEvent = cutoffHoursBeforeEvent;
        this.updatedAt = updatedAt;
    }

    public UUID getEventId() { return eventId; }
    public Long getOrganizerId() { return organizerId; }
    public boolean isRefundsAllowed() { return refundsAllowed; }
    public int getCutoffHoursBeforeEvent() { return cutoffHoursBeforeEvent; }
    public Instant getUpdatedAt() { return updatedAt; }
}
