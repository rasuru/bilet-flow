package com.biletflow.biletflow.eventmanagement.application.cancellation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.events.EventBus;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.application.audit.*;
import com.biletflow.biletflow.eventmanagement.application.socialevent.EventService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.command.CancelSocialEventCommand;
import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.InvalidEventStateException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EventCancellationTest {

    private final Instant now = Instant.parse("2026-10-20T12:00:00Z");
    private final SocialEventRepository repository = mock(SocialEventRepository.class);
    private final EventAuditService audit = mock(EventAuditService.class);
    private final EventBus bus = mock(EventBus.class);
    private final CurrentActor actor = mock(CurrentActor.class);
    private final TransactionLocks locks = mock(TransactionLocks.class);
    private final EventService service = new EventService(
        repository,
        mock(VenueLayoutRepository.class),
        audit,
        actor,
        Clock.fixed(now, ZoneOffset.UTC),
        locks,
        bus
    );

    private SocialEvent event() {
        var event = SocialEvent.createDraft(
            7L,
            "Meetup",
            "Description",
            "Technology",
            "https://example.com/image.png",
            EventVisibility.PUBLIC,
            new EventDateRange(now.plusSeconds(3600), now.plusSeconds(7200)),
            new RegistrationWindow.AlwaysOpen()
        );
        event.attachVenue(Venue.createGeneralAdmission("Venue", "Address", 10));
        event.publish();
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        return event;
    }

    @Test
    void cancellationPublishesOneDurableFactAndAuditEvenWhenRetriedAfterEventEnd() {
        var event = event();
        when(actor.requireUserId()).thenReturn(7L);
        service.handle(new CancelSocialEventCommand(event.getId(), now));
        service.handle(new CancelSocialEventCommand(event.getId(), now.plusSeconds(10000)));
        assertEquals(SocialEventStatus.CANCELLED, event.getStatus());
        verify(bus, times(1)).publish(any());
        verify(repository, times(1)).save(event);
        verify(audit, times(1)).record(eq(event.getId().value()), eq(7L), eq(EventAuditRepository.EventAuditType.CANCELLED), anyString());
        var order = inOrder(locks, repository, bus);
        order.verify(locks).lock("event-lifecycle:" + event.getId().value());
        order.verify(repository).findById(event.getId());
        order.verify(repository).save(event);
        order.verify(bus).publish(any());
    }

    @Test
    void anotherUserCannotCancelOrPublishNotices() {
        var event = event();
        when(actor.requireUserId()).thenReturn(8L);
        assertThrows(AuthorizationException.class, () -> service.handle(new CancelSocialEventCommand(event.getId(), now)));
        assertEquals(SocialEventStatus.PUBLISHED, event.getStatus());
        verifyNoInteractions(bus, audit);
    }

    @Test
    void firstCancellationAfterEventEndIsRejected() {
        var event = event();
        when(actor.requireUserId()).thenReturn(7L);
        assertThrows(InvalidEventStateException.class, () ->
            service.handle(new CancelSocialEventCommand(event.getId(), now.plusSeconds(10000)))
        );
        verifyNoInteractions(bus, audit);
    }
}
