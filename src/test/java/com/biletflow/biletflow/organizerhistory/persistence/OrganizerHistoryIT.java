package com.biletflow.biletflow.organizerhistory.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSession;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionId;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionRepository;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import com.biletflow.biletflow.organizerhistory.application.HistoryCategory;
import com.biletflow.biletflow.organizerhistory.application.HistoryEntry;
import com.biletflow.biletflow.organizerhistory.application.HistoryPage;
import com.biletflow.biletflow.organizerhistory.application.HistoryQuery;
import com.biletflow.biletflow.organizerhistory.application.OrganizerHistoryService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class OrganizerHistoryIT {

    @MockitoBean
    EventManagementOhs events;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    CurrentActor actor;

    @Autowired
    OrganizerHistoryService history;

    @Autowired
    CheckoutSessionRepository checkouts;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    JdbcTemplate jdbc;

    private final long organizer = ThreadLocalRandom.current().nextLong(100000, Long.MAX_VALUE);
    private final long stranger = organizer - 1;
    private final UUID eventId = UUID.randomUUID();
    private final UUID otherEventId = UUID.randomUUID();
    private final UUID checkoutId = UUID.randomUUID();
    private final UUID otherCheckoutId = UUID.randomUUID();
    private final UUID caseId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();
    private final Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(10, ChronoUnit.DAYS);

    @BeforeEach
    void timelineOfTwoEvents() {
        doReturn(Optional.of(organizer)).when(actor).userId();
        when(events.canManageTicketing(eventId, organizer)).thenReturn(true);
        when(events.canManageTicketing(eventId, stranger)).thenReturn(false);

        tx().executeWithoutResult(status -> {
            checkouts.save(new CheckoutSession(new CheckoutSessionId(checkoutId), 5L, eventId, InventoryMode.GENERAL_ADMISSION));
            checkouts.save(new CheckoutSession(new CheckoutSessionId(otherCheckoutId), 5L, otherEventId, InventoryMode.GENERAL_ADMISSION));

            eventLog(eventId, at(1), "PUBLISHED", "Event published");
            eventLog(eventId, at(9), "CANCELLED", "Event cancelled");
            eventLog(eventId, at(6), "SUPPORT_CASE_STATUS_CHANGED", "Support case status changed from OPEN to RESOLVED");
            eventLog(otherEventId, at(2), "PUBLISHED", "Other event published");

            jdbc.update(
                "insert into promotions_activity_log (id, event_id, timestamp, actor_user_id, type, description) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                eventId,
                at(3),
                organizer,
                "PROMO_CODE_DISABLED",
                "Promo code SPRING30 disabled"
            );
            jdbc.update(
                "insert into ticket_inventory_activity_log (id, event_id, timestamp, actor_user_id, type, entity_id, description) values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                eventId,
                at(2),
                organizer,
                "TICKET_PRICE_CHANGED",
                UUID.randomUUID(),
                "Price changed from 1000 KZT to 1500 KZT"
            );
            jdbc.update(
                "insert into ticket_inventory_activity_log (id, event_id, timestamp, actor_user_id, type, entity_id, description) values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                eventId,
                at(7),
                organizer,
                "TICKET_CHECKED_IN",
                ticketId,
                "Ticket checked in"
            );

            financial("ordercheckout", checkoutId, at(4), organizer, "ORDER_REFUND_REQUESTED", null);
            financial("ordercheckout", checkoutId, at(4), null, "PAID_ORDER_COMPLETED", null);
            financial("ordercheckout", otherCheckoutId, at(4), organizer, "ORDER_REFUND_REQUESTED", null);
            financial("payments", eventId, at(8), 1L, "PAID_SALES_SUSPENDED", "Chargeback review");

            jdbc.update(
                "insert into support_case (id, case_type, requester_user_id, subject, category, event_id, status, created_at, updated_at, version) values (?, 'ATTENDEE_SUPPORT', 5, 'Help', 'REFUND', ?, 'OPEN', ?, ?, 0)",
                caseId,
                eventId,
                at(5),
                at(5)
            );
            support(at(5), 5L, "CASE_OPENED", "Support case opened: ATTENDEE_SUPPORT");
            support(at(5), 5L, "MESSAGE_ADDED", "Message added by ATTENDEE");
            support(at(6), organizer, "CASE_STATUS_CHANGED", "OPEN -> RESOLVED");
        });
    }

    @AfterEach
    void cleanup() {
        tx().executeWithoutResult(status -> {
            jdbc.update("delete from event_activity_log where event_id in (?, ?)", eventId, otherEventId);
            jdbc.update("delete from promotions_activity_log where event_id = ?", eventId);
            jdbc.update("delete from ticket_inventory_activity_log where event_id = ?", eventId);
            jdbc.update("delete from common_financial_audit where aggregate_id in (?, ?, ?)", checkoutId, otherCheckoutId, eventId);
            jdbc.update("delete from support_case_audit where support_case_id = ?", caseId);
            jdbc.update("delete from support_case where id = ?", caseId);
            jdbc.update("delete from oc_checkout_item where purchase_id in (?, ?)", checkoutId, otherCheckoutId);
            jdbc.update("delete from oc_checkout where id in (?, ?)", checkoutId, otherCheckoutId);
        });
    }

    @Test
    void timelineMergesEveryContextNewestFirst() {
        HistoryPage page = history.history(HistoryQuery.all(eventId));

        assertEquals(
            List.of(
                "CANCELLED",
                "PAID_SALES_SUSPENDED",
                "TICKET_CHECKED_IN",
                "SUPPORT_CASE_STATUS_CHANGED",
                "SUPPORT_CASE_OPENED",
                "ORDER_REFUND_REQUESTED",
                "PROMO_CODE_DISABLED",
                "TICKET_PRICE_CHANGED",
                "PUBLISHED"
            ),
            page.entries().stream().map(HistoryEntry::action).toList()
        );
        assertEquals(9, page.total());

        HistoryEntry checkIn = page.entries().get(2);
        assertEquals(HistoryCategory.CHECK_IN, checkIn.category());
        assertEquals("TICKET", checkIn.entityType());
        assertEquals(ticketId, checkIn.entityId());
        assertEquals(organizer, checkIn.actorUserId());
        assertEquals(at(7).toInstant(), checkIn.timestamp());

        HistoryEntry refund = page.entries().get(5);
        assertEquals(HistoryCategory.REFUNDS, refund.category());
        assertEquals(checkoutId, refund.entityId());

        HistoryEntry suspension = page.entries().get(1);
        assertEquals("Paid sales suspended: Chargeback review", suspension.description());

        HistoryEntry opened = page.entries().get(4);
        assertEquals(caseId, opened.entityId());
        assertEquals(HistoryCategory.SUPPORT, opened.category());

        HistoryEntry statusChange = page.entries().get(3);
        assertEquals(HistoryCategory.SUPPORT, statusChange.category());
        assertEquals("SUPPORT_CASE", statusChange.entityType());
        assertEquals(caseId, statusChange.entityId());
        assertEquals("Support case status changed: OPEN -> RESOLVED", statusChange.description());
    }

    @Test
    void timeBoundariesAreAppliedInEverySource() {
        var exact = history.history(new HistoryQuery(eventId, at(4).toInstant(), at(5).toInstant(), null, null, 0, 50));
        assertEquals(List.of("ORDER_REFUND_REQUESTED"), exact.entries().stream().map(HistoryEntry::action).toList());
        assertEquals(1, exact.total());

        var empty = history.history(
            new HistoryQuery(eventId, at(4).toInstant(), at(4).toInstant().plusNanos(1000), Set.of(HistoryCategory.CHECK_IN), null, 0, 50)
        );
        assertEquals(0, empty.total());
        assertTrue(empty.entries().isEmpty());
    }

    @Test
    void everyPageAcrossSourcesIsDisjointAndOrdered() {
        List<String> walked = new ArrayList<>();
        for (int page = 0; page < 5; page++) {
            var slice = history.history(new HistoryQuery(eventId, null, null, null, null, page, 2));
            assertEquals(9, slice.total());
            slice.entries().forEach(entry -> walked.add(entry.action()));
        }
        assertEquals(history.history(HistoryQuery.all(eventId)).entries().stream().map(HistoryEntry::action).toList(), walked);
    }

    @Test
    void timelineFiltersByDateRangeCategoryAndAction() {
        var range = history.history(new HistoryQuery(eventId, at(3).toInstant(), at(7).toInstant(), null, null, 0, 50));
        assertEquals(
            List.of("SUPPORT_CASE_STATUS_CHANGED", "SUPPORT_CASE_OPENED", "ORDER_REFUND_REQUESTED", "PROMO_CODE_DISABLED"),
            range.entries().stream().map(HistoryEntry::action).toList()
        );

        var support = history.history(new HistoryQuery(eventId, null, null, Set.of(HistoryCategory.SUPPORT), null, 0, 50));
        assertEquals(2, support.total());

        var cancelled = history.history(new HistoryQuery(eventId, null, null, null, Set.of("cancelled"), 0, 50));
        assertEquals(List.of("CANCELLED"), cancelled.entries().stream().map(HistoryEntry::action).toList());
    }

    @Test
    void timelineIsPaged() {
        var second = history.history(new HistoryQuery(eventId, null, null, null, null, 1, 4));
        assertEquals(9, second.total());
        assertEquals(
            List.of("SUPPORT_CASE_OPENED", "ORDER_REFUND_REQUESTED", "PROMO_CODE_DISABLED", "TICKET_PRICE_CHANGED"),
            second.entries().stream().map(HistoryEntry::action).toList()
        );
    }

    @Test
    void strangersCannotReadTheTimelineButAdministratorsCan() {
        doReturn(Optional.of(stranger)).when(actor).userId();
        assertThrows(AuthorizationException.class, () -> history.history(HistoryQuery.all(eventId)));
        verify(events).canManageTicketing(eventId, stranger);

        doReturn(true).when(actor).isAdministrator();
        assertEquals(9, history.history(HistoryQuery.all(eventId)).total());
    }

    private Timestamp at(int hours) {
        return Timestamp.from(base.plus(hours, ChronoUnit.HOURS));
    }

    private void eventLog(UUID event, Timestamp when, String type, String description) {
        jdbc.update(
            "insert into event_activity_log (id, event_id, timestamp, actor_user_id, type, description) values (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            event,
            when,
            organizer,
            type,
            description
        );
    }

    private void financial(String context, UUID aggregate, Timestamp when, Long actorId, String action, String details) {
        jdbc.update(
            "insert into common_financial_audit (id, context, aggregate_id, actor_id, action, occurred_at, details) values (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            context,
            aggregate,
            actorId,
            action,
            when,
            details
        );
    }

    private void support(Timestamp when, Long actorId, String action, String details) {
        jdbc.update(
            "insert into support_case_audit (id, support_case_id, event_id, occurred_at, actor_user_id, action, details) values (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            caseId,
            eventId,
            when,
            actorId,
            action,
            details
        );
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }
}
