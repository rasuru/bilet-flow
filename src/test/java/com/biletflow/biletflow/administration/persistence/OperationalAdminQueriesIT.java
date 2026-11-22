package com.biletflow.biletflow.administration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.administration.application.OperationalReportService;
import com.biletflow.biletflow.common.application.QueryPage;
import com.biletflow.biletflow.common.ohs.OperationalCountsOhs;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.application.socialevent.EventAdminQueries;
import com.biletflow.biletflow.eventmanagement.application.socialevent.EventReportQueries;
import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.domain.report.EventReport;
import com.biletflow.biletflow.eventmanagement.domain.report.EventReportRepository;
import com.biletflow.biletflow.ordercheckout.application.OrderAdminQueries;
import com.biletflow.biletflow.ordercheckout.domain.order.OrderStatus;
import com.biletflow.biletflow.support.application.SupportAdminQueries;
import com.biletflow.biletflow.support.domain.supportcase.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class OperationalAdminQueriesIT {

    @Autowired
    private SocialEventRepository events;

    @Autowired
    private EventAdminQueries adminEvents;

    @Autowired
    private EventReportRepository reports;

    @Autowired
    private EventReportQueries adminReports;

    @Autowired
    private SupportCaseRepository cases;

    @Autowired
    private SupportAdminQueries adminCases;

    @Autowired
    private OrderAdminQueries adminOrders;

    @Autowired
    private List<OperationalCountsOhs> operations;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entities;

    @Test
    void moderationQueriesUsePersistedStatesAndExportEventScopedCounts() {
        Instant now = Instant.parse("2026-11-22T09:00:00Z");
        long organizer = 301L;
        var event = SocialEvent.createDraft(
            organizer, "Moderation Case 100%", "", "Community", "", EventVisibility.PRIVATE,
            new EventDateRange(now.plusSeconds(3600), now.plusSeconds(7200)),
            new RegistrationWindow.AlwaysOpen()
        );
        event.suspend();
        events.save(event);
        UUID eventId = event.getId().value();
        var report = EventReport.submit(eventId, 302, "Review fixture", now)
            .review(EventReport.Decision.CONFIRM, 303, "Confirmed by staff", now.plusSeconds(2));
        reports.save(report);

        var privateCase = openCase(eventId, now, "Private attendee case");
        cases.save(privateCase);
        var escalatedCase = openCase(eventId, now, "Escalated attendee case");
        escalatedCase.escalate(now.plusSeconds(5));
        cases.save(escalatedCase);
        entities.flush();
        entities.clear();

        QueryPage page = new QueryPage(0, 10);
        assertThat(adminEvents.search(new EventAdminQueries.Filter("100%", SocialEventStatus.SUSPENDED, null, organizer), page))
            .extracting(EventAdminQueries.Summary::id)
            .contains(eventId);
        assertThat(events.findById(event.getId()).orElseThrow().getStatusBeforeSuspension()).isEqualTo(SocialEventStatus.DRAFT);
        assertThat(adminReports.search(eventId, EventReport.Status.CONFIRMED, page))
            .extracting(EventReportQueries.View::reviewNote)
            .containsExactly("Confirmed by staff");
        assertThat(adminCases.search(null, eventId, true, page))
            .extracting(SupportAdminQueries.Summary::id)
            .containsExactly(escalatedCase.getId().value());
        assertThat(reportService().countByStatus(eventId)).containsExactly(
            new OperationalCountsOhs.Count("event_reports", "CONFIRMED", 1),
            new OperationalCountsOhs.Count("events", "SUSPENDED", 1),
            new OperationalCountsOhs.Count("platform_support_cases", "OPEN", 1)
        );
    }

    @Test
    void promotionActivityIncludesAttributedOrdersOnly() {
        UUID event = UUID.randomUUID();
        UUID promotion = UUID.randomUUID();
        UUID discountedOrder = UUID.randomUUID();
        insertOrder(discountedOrder, event, promotion);
        insertOrder(UUID.randomUUID(), event, null);

        var filter = new OrderAdminQueries.Filter(null, event, null, OrderStatus.COMPLETED, null, true);
        assertThat(adminOrders.search(filter, new QueryPage(0, 10)))
            .extracting(OrderAdminQueries.View::id)
            .containsExactly(discountedOrder);
        assertThat(reportService().countByStatus(event)).contains(new OperationalCountsOhs.Count("orders", "COMPLETED", 2));
    }

    private OperationalReportService reportService() {
        CurrentActor administrator = new CurrentActor() {
            @Override
            public Optional<Long> userId() {
                return Optional.of(303L);
            }

            @Override
            public boolean isAdministrator() {
                return true;
            }
        };
        return new OperationalReportService(operations, administrator);
    }

    private void insertOrder(UUID id, UUID event, UUID promotion) {
        jdbc.update(
            """
            insert into oc_order (
                id, event_id, owner_id, status, inventory_mode, promotion_id,
                discount_amount, discount_currency, total_amount, total_currency, version
            ) values (?, ?, 301, 'COMPLETED', 'GENERAL_ADMISSION', ?, ?, 'KZT', 900, 'KZT', 0)
            """,
            id, event, promotion, promotion == null ? 0 : 100
        );
    }

    private SupportCase openCase(UUID eventId, Instant now, String subject) {
        return SupportCase.open(
            SupportCaseType.ATTENDEE_SUPPORT, 302L, subject, IssueCategory.PAYMENT, "First message",
            eventId, null, null, now
        );
    }
}
