package com.biletflow.biletflow.eventmanagement.application.cancellation;

import com.biletflow.biletflow.common.events.IntegrationEvent;
import com.biletflow.biletflow.common.events.IntegrationEventHandler;
import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import com.biletflow.biletflow.eventmanagement.ohs.EventCancelled;
import java.util.LinkedHashSet;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Fan-out and outbox acknowledgement commit together; SMTP delivery is a separate worker. */
@Component
public class CancellationNotices implements IntegrationEventHandler {

    private final SocialEventRepository events;
    private final CancellationRecipients recipients;
    private final CancellationNoticeStore notices;

    public CancellationNotices(SocialEventRepository events, CancellationRecipients recipients, CancellationNoticeStore notices) {
        this.events = Objects.requireNonNull(events);
        this.recipients = Objects.requireNonNull(recipients);
        this.notices = Objects.requireNonNull(notices);
    }

    @Override
    public String eventType() {
        return EventCancelled.TYPE;
    }

    @Override
    @Transactional
    public void handle(IntegrationEvent message) {
        var cancelled = EventCancelled.from(message);
        var event = events
            .findById(new SocialEventId(cancelled.eventId()))
            .orElseThrow(() -> new SocialEventNotFoundException(cancelled.eventId().toString()));
        if (event.getStatus() != SocialEventStatus.CANCELLED) throw new IllegalStateException("Event is not cancelled");
        var emails = new LinkedHashSet<>(recipients.attendees(cancelled.eventId()));
        recipients.verifiedEmail(event.getOrganizerId()).ifPresent(emails::add);
        for (var staff : event.getStaffAssignments()) recipients.verifiedEmail(staff.userId()).ifPresent(emails::add);
        for (String email : emails)
            notices.enqueue(
                CancellationNotice.forRecipient(
                    cancelled.eventId(),
                    email,
                    event.getTitle(),
                    event.getDateRange().startAt(),
                    event.getDateRange().endAt(),
                    cancelled.cancelledAt()
                )
            );
    }
}
