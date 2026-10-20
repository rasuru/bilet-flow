package com.biletflow.biletflow.eventmanagement.persistence.cancellation;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.events.EventBus;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditService;
import com.biletflow.biletflow.eventmanagement.application.cancellation.*;
import com.biletflow.biletflow.eventmanagement.application.socialevent.EventService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.command.CancelSocialEventCommand;
import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.ohs.EventCancelled;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class EventCancellationPersistenceIT {

    @Autowired
    SocialEventRepository events;

    @Autowired
    VenueLayoutRepository layouts;

    @Autowired
    EventAuditService audit;

    @Autowired
    TransactionLocks locks;

    @Autowired
    EventBus bus;

    @Autowired
    CancellationNoticeStore notices;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    private final Set<UUID> noticeIds = new HashSet<>();
    private final Set<UUID> eventIds = new HashSet<>();

    private TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }

    private CancellationNotice notice() {
        var n = CancellationNotice.forRecipient(
            UUID.randomUUID(),
            "attendee@example.com",
            "Cancelled event",
            now.plusSeconds(3600),
            now.plusSeconds(7200),
            now
        );
        noticeIds.add(n.id());
        return n;
    }

    @AfterEach
    void cleanup() {
        tx().executeWithoutResult(status -> {
            for (UUID id : noticeIds) jdbc.update("delete from event_management_cancellation_notice where id=?", id);
            for (UUID id : eventIds) {
                jdbc.update("delete from common_outbox where id=?", new EventCancelled(id, now).toEvent().id());
                jdbc.update("delete from event_activity_log where event_id=?", id);
                events.deleteById(new SocialEventId(id));
            }
        });
    }

    @Test
    void eventStateAuditAndDurableFactRollbackTogetherAndRetryPublishesOnce() {
        var event = SocialEvent.createDraft(
            7L,
            "Cancelled event",
            "Description",
            "Technology",
            "https://example.com/image.png",
            EventVisibility.PUBLIC,
            new EventDateRange(now.plusSeconds(3600), now.plusSeconds(7200)),
            new RegistrationWindow.AlwaysOpen()
        );
        event.attachVenue(Venue.createGeneralAdmission("Venue", "Address", 10));
        event.publish();
        eventIds.add(event.getId().value());
        tx().executeWithoutResult(status -> events.save(event));
        var service = new EventService(events, layouts, audit, () -> Optional.of(7L), Clock.fixed(now, ZoneOffset.UTC), locks, bus);
        var command = new CancelSocialEventCommand(event.getId(), now);
        assertThrows(IllegalStateException.class, () ->
            tx().executeWithoutResult(status -> {
                service.handle(command);
                throw new IllegalStateException("Rollback cancellation");
            })
        );
        assertEquals(SocialEventStatus.PUBLISHED, tx().execute(status -> events.findById(event.getId()).orElseThrow().getStatus()));
        UUID factId = new EventCancelled(event.getId().value(), now).toEvent().id();
        assertEquals(0, jdbc.queryForObject("select count(*) from common_outbox where id=?", Integer.class, factId));
        tx().executeWithoutResult(status -> service.handle(command));
        tx().executeWithoutResult(status -> service.handle(command));
        assertEquals(1, jdbc.queryForObject("select count(*) from common_outbox where id=?", Integer.class, factId));
        assertEquals(
            1,
            jdbc.queryForObject("select count(*) from event_activity_log where event_id=?", Integer.class, event.getId().value())
        );
    }

    @Test
    void noticeFanoutIsTransactionalAndRepeatedEnqueueDoesNotResetDelivery() {
        var n = notice();
        assertThrows(IllegalStateException.class, () ->
            tx().executeWithoutResult(status -> {
                notices.enqueue(n);
                throw new IllegalStateException("Rollback fanout");
            })
        );
        assertEquals(0, jdbc.queryForObject("select count(*) from event_management_cancellation_notice where id=?", Integer.class, n.id()));
        tx().executeWithoutResult(status -> {
            notices.enqueue(n);
            notices.enqueue(n);
        });
        var claim = notices.claim(now).orElseThrow();
        assertEquals(n.id(), claim.notice().id());
        notices.complete(claim, now.plusSeconds(1));
        tx().executeWithoutResult(status -> notices.enqueue(n));
        assertTrue(notices.claim(now.plusSeconds(1000)).isEmpty());
    }

    @Test
    void parallelWorkersCannotClaimTheSameNoticeAndAnExpiredWorkerCannotAcknowledgeIt() throws Exception {
        var n = notice();
        tx().executeWithoutResult(status -> notices.enqueue(n));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<Optional<CancellationNoticeStore.Claim>> claim = () -> {
                start.await();
                return notices.claim(now);
            };
            var first = pool.submit(claim);
            var second = pool.submit(claim);
            start.countDown();
            var a = first.get(10, TimeUnit.SECONDS);
            var b = second.get(10, TimeUnit.SECONDS);
            assertNotEquals(a.isPresent(), b.isPresent());
            var old = a.orElseGet(b::orElseThrow);
            var replacement = notices.claim(now.plusSeconds(121)).orElseThrow();
            notices.complete(old, now.plusSeconds(122));
            assertEquals(
                1,
                jdbc.queryForObject(
                    "select count(*) from event_management_cancellation_notice where id=? and sent_at is null",
                    Integer.class,
                    n.id()
                )
            );
            notices.retry(replacement, now.plusSeconds(122));
            assertTrue(notices.claim(now.plusSeconds(181)).isEmpty());
            var retry = notices.claim(now.plusSeconds(182)).orElseThrow();
            assertEquals(n.id(), retry.notice().id());
            notices.complete(retry, now.plusSeconds(183));
        } finally {
            pool.shutdownNow();
        }
    }
}
