package com.biletflow.biletflow.eventmanagement.application.socialevent;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.application.QueryPage;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventRepository;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import com.biletflow.biletflow.eventmanagement.domain.report.EventReport;
import com.biletflow.biletflow.eventmanagement.domain.report.EventReportRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventReportService {

    private final SocialEventRepository events;
    private final EventReportRepository reports;
    private final EventReportQueries queries;
    private final CurrentActor actor;
    private final TransactionLocks locks;
    private final Clock clock;
    private final PrivateAttendeeAccess attendeeAccess;

    public EventReportService(
        SocialEventRepository events,
        EventReportRepository reports,
        EventReportQueries queries,
        CurrentActor actor,
        TransactionLocks locks,
        Clock clock,
        PrivateAttendeeAccess attendeeAccess
    ) {
        this.events = events;
        this.reports = reports;
        this.queries = queries;
        this.actor = actor;
        this.locks = locks;
        this.clock = clock;
        this.attendeeAccess = attendeeAccess;
    }

    @Transactional
    public UUID submit(UUID eventId, String reason) {
        long reporter = actor.requireUserId();
        locks.lock("event-lifecycle:" + eventId);
        var existing = reports.findByReporter(eventId, reporter);
        if (existing.isPresent()) return existing.get().id();

        events.findById(new SocialEventId(eventId))
            .filter(event -> attendeeAccess.canReadEvent(event, reporter))
            .orElseThrow(() -> new SocialEventNotFoundException(eventId.toString()));
        var report = EventReport.submit(eventId, reporter, reason, clock.instant());
        reports.save(report);
        return report.id();
    }

    public List<EventReportQueries.View> search(UUID eventId, EventReport.Status status, int offset, int limit) {
        actor.requireAdministrator();
        return queries.search(eventId, status, new QueryPage(offset, limit));
    }

    public EventReportQueries.View get(UUID reportId) {
        actor.requireAdministrator();
        return EventReportQueries.View.from(requireReport(reportId));
    }

    @Transactional
    public EventReportQueries.View review(UUID reportId, EventReport.Decision decision, String note) {
        long admin = actor.requireAdministrator();
        var report = requireReport(reportId);
        var reviewed = report.review(decision, admin, note, clock.instant());
        if (reviewed != report) reports.save(reviewed);
        return EventReportQueries.View.from(reviewed);
    }

    private EventReport requireReport(UUID reportId) {
        return reports.find(reportId).orElseThrow(() -> new EntityNotFoundException("Event report not found: " + reportId));
    }
}
