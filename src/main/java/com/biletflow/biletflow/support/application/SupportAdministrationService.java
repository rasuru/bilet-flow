package com.biletflow.biletflow.support.application;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.QueryPage;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.support.application.event.SupportCaseEscalatedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseOpenedEvent;
import com.biletflow.biletflow.support.domain.exceptions.SupportCaseNotFoundException;
import com.biletflow.biletflow.support.domain.supportcase.*;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SupportAdministrationService {

    private final SupportAdminQueries queries;
    private final SupportCaseRepository cases;
    private final CurrentActor actor;
    private final EventManagementOhs events;
    private final TransactionLocks locks;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;

    public SupportAdministrationService(
        SupportAdminQueries queries,
        SupportCaseRepository cases,
        CurrentActor actor,
        EventManagementOhs events,
        TransactionLocks locks,
        Clock clock,
        ApplicationEventPublisher publisher
    ) {
        this.queries = queries;
        this.cases = cases;
        this.actor = actor;
        this.events = events;
        this.locks = locks;
        this.clock = clock;
        this.publisher = publisher;
    }

    public List<SupportAdminQueries.Summary> search(CaseStatus status, UUID eventId, boolean escalatedOnly, int offset, int limit) {
        actor.requireAdministrator();
        return queries.search(status, eventId, escalatedOnly, new QueryPage(offset, limit));
    }

    public SupportAdminQueries.Detail get(UUID caseId) {
        actor.requireAdministrator();
        SupportCase supportCase = requireCase(caseId);
        if (supportCase.getType() != SupportCaseType.PLATFORM_SUPPORT && supportCase.getEscalatedAt() == null) {
            throw new SupportCaseNotFoundException(caseId.toString());
        }
        return new SupportAdminQueries.Detail(
            SupportAdminQueries.Summary.from(supportCase),
            supportCase.getMessages().stream()
                .map(message -> new SupportAdminQueries.Message(
                    message.id(), message.authorUserId(), message.authorType(), message.content(), message.sentAt()
                ))
                .toList()
        );
    }

    @Transactional
    public UUID openPlatformCase(String subject, IssueCategory category, String message, UUID eventId) {
        Long requester = actor.requireUserId();
        if (eventId != null && !events.canManageTicketing(eventId, requester)) {
            throw new AuthorizationException("Only the event organizer can link this event to a platform support case");
        }
        if (subject == null || subject.length() > 255 || message == null || message.length() > 4000 || category == null) {
            throw new IllegalArgumentException("A category, subject of up to 255 characters and message of up to 4000 characters are required");
        }
        var now = clock.instant();
        SupportCase opened = cases.save(SupportCase.open(
            SupportCaseType.PLATFORM_SUPPORT, requester, subject, category, message, eventId, null, null, now
        ));
        UUID caseId = opened.getId().value();
        publisher.publishEvent(new SupportCaseOpenedEvent(caseId, requester, opened.getType(), eventId, now));
        return caseId;
    }

    @Transactional
    public void escalate(UUID caseId) {
        Long organizer = actor.requireUserId();
        locks.lock("support-case:" + caseId);
        SupportCase supportCase = requireCase(caseId);
        if (supportCase.getEventId() == null || !events.canManageTicketing(supportCase.getEventId(), organizer)) {
            throw new SupportCaseNotFoundException(caseId.toString());
        }
        var now = clock.instant();
        if (supportCase.escalate(now)) {
            cases.save(supportCase);
            publisher.publishEvent(new SupportCaseEscalatedEvent(
                caseId, organizer, supportCase.getRequesterUserId(), supportCase.getEventId(), now
            ));
        }
    }

    private SupportCase requireCase(UUID caseId) {
        return cases.findById(new SupportCaseId(caseId)).orElseThrow(() -> new SupportCaseNotFoundException(caseId.toString()));
    }
}
