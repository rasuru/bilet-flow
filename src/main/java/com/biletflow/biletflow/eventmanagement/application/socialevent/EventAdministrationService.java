package com.biletflow.biletflow.eventmanagement.application.socialevent;

import com.biletflow.biletflow.common.application.QueryPage;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditRepository.EventAuditType;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.view.ManagedEventView;
import com.biletflow.biletflow.eventmanagement.domain.SocialEvent;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventRepository;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventAdministrationService {

    private final EventAdminQueries queries;
    private final SocialEventRepository events;
    private final CurrentActor actor;
    private final TransactionLocks locks;
    private final EventAuditService audit;

    public EventAdministrationService(
        EventAdminQueries queries,
        SocialEventRepository events,
        CurrentActor actor,
        TransactionLocks locks,
        EventAuditService audit
    ) {
        this.queries = queries;
        this.events = events;
        this.actor = actor;
        this.locks = locks;
        this.audit = audit;
    }

    public List<EventAdminQueries.Summary> search(EventAdminQueries.Filter filter, int offset, int limit) {
        actor.requireAdministrator();
        return queries.search(filter, new QueryPage(offset, limit));
    }

    public ManagedEventView get(UUID eventId) {
        actor.requireAdministrator();
        return EventQueryService.toManagedView(requireEvent(eventId));
    }

    @Transactional
    public ManagedEventView suspend(UUID eventId, String reason) {
        Long admin = actor.requireAdministrator();
        if (reason == null || reason.isBlank() || reason.length() > 200) {
            throw new IllegalArgumentException("A suspension reason of up to 200 characters is required");
        }

        locks.lock("event-lifecycle:" + eventId);
        SocialEvent event = requireEvent(eventId);
        if (event.suspend()) {
            events.save(event);
            audit.record(eventId, admin, EventAuditType.SUSPENDED, reason.strip());
        }
        return EventQueryService.toManagedView(event);
    }

    @Transactional
    public ManagedEventView reinstate(UUID eventId) {
        Long admin = actor.requireAdministrator();
        locks.lock("event-lifecycle:" + eventId);
        SocialEvent event = requireEvent(eventId);
        if (event.reinstate()) {
            events.save(event);
            audit.record(eventId, admin, EventAuditType.REINSTATED, "Event reinstated");
        }
        return EventQueryService.toManagedView(event);
    }

    private SocialEvent requireEvent(UUID eventId) {
        return events.findById(new SocialEventId(eventId)).orElseThrow(() -> new SocialEventNotFoundException(eventId.toString()));
    }
}
