package com.biletflow.biletflow.ordercheckout.application.refundpolicy;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ordercheckout.persistence.refundpolicy.RefundPolicyJpaEntity;
import com.biletflow.biletflow.ordercheckout.persistence.refundpolicy.RefundPolicyJpaRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RefundPolicyService  {

    private static final int DEFAULT_CUTOFF_HOURS = 24;
    private final RefundPolicyJpaRepository policies;
    private final EventManagementOhs events;
    private final CurrentActor actor;
    private final Clock clock;

    public RefundPolicyService(RefundPolicyJpaRepository policies, EventManagementOhs events, CurrentActor actor, Clock clock) {
        this.policies = Objects.requireNonNull(policies);
        this.events = Objects.requireNonNull(events);
        this.actor = Objects.requireNonNull(actor);
        this.clock = Objects.requireNonNull(clock);
    }

    public RefundPolicyView get(UUID eventId) {
        Objects.requireNonNull(eventId);
        // Resolve the event first: unknown event IDs must not receive a synthetic default policy.
        events.salesScope(eventId, clock.instant());
        return policies.findById(eventId).map(this::view)
            .orElseGet(() -> new RefundPolicyView(eventId, false, DEFAULT_CUTOFF_HOURS, null));
    }

    @Transactional
    public RefundPolicyView update(UUID eventId, boolean refundsAllowed, int cutoffHoursBeforeEvent) {
        return update(eventId, refundsAllowed, cutoffHoursBeforeEvent, null);
    }

    @Transactional
    public RefundPolicyView update(UUID eventId, boolean refundsAllowed, int cutoffHoursBeforeEvent, String terms) {
        Long organizerId = actor.requireUserId();
        if (!events.canManageTicketing(eventId, organizerId)) {
            throw new AuthorizationException("Only the event organizer can update its refund policy");
        }
        if (cutoffHoursBeforeEvent < 0 || cutoffHoursBeforeEvent > 24 * 30) {
            throw new IllegalArgumentException("Refund cutoff must be between 0 and 720 hours");
        }
        var current = policies.findById(eventId).orElseGet(() -> new RefundPolicyJpaEntity(
            eventId, organizerId, refundsAllowed, cutoffHoursBeforeEvent, clock.instant()
        ));
        current.update(refundsAllowed, cutoffHoursBeforeEvent, terms, clock.instant());
        return view(policies.save(current));
    }

    private RefundPolicyView view(RefundPolicyJpaEntity entity) {
        return new RefundPolicyView(entity.getEventId(), entity.isRefundsAllowed(), entity.getCutoffHoursBeforeEvent(), entity.getTerms(), entity.getUpdatedAt());
    }
}
