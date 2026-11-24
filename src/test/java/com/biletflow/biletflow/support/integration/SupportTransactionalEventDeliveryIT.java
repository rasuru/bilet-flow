package com.biletflow.biletflow.support.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.common.events.JdbcEventBus;
import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import com.biletflow.biletflow.support.application.event.SupportCaseOpenedEvent;
import com.biletflow.biletflow.support.domain.supportcase.IssueCategory;
import com.biletflow.biletflow.support.domain.supportcase.SupportCase;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseRepository;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class SupportTransactionalEventDeliveryIT {

    private static final UUID EVENT_ID = UUID.fromString("3191d7f0-46e3-4de2-b844-2bc2c3759e86");

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JdbcEventBus outbox;

    @Autowired
    private SupportCaseRepository supportCases;

    @MockitoBean
    private IamOhs iam;

    @MockitoBean
    private EventManagementOhs eventManagement;

    @MockitoBean
    private EmailSender emailSender;

    private UUID caseId;

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        if (caseId == null) return;
        tx().executeWithoutResult(status -> {
            jdbc.update("delete from common_outbox where payload like ?", "%" + caseId + "%");
            jdbc.update("delete from support_case_audit where support_case_id = ?", caseId);
            jdbc.update("delete from support_case_message where support_case_id = ?", caseId);
            jdbc.update("delete from support_case where id = ?", caseId);
        });
    }

    private SupportCase createCase(Long requesterId, Instant now) {
        SupportCase supportCase = SupportCase.open(
            SupportCaseType.ATTENDEE_SUPPORT,
            requesterId,
            "Test support case",
            IssueCategory.OTHER,
            "Initial request",
            EVENT_ID,
            null,
            null,
            now
        );
        caseId = supportCase.getId().value();
        tx().executeWithoutResult(status -> supportCases.save(supportCase));
        return supportCase;
    }

    @Test
    void beforeCommitListenersWriteSupportAuditAndDurableNotificationInSameTransaction() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        SupportCase supportCase = createCase(701L, now);
        SupportCaseOpenedEvent event = new SupportCaseOpenedEvent(caseId, 701L, SupportCaseType.ATTENDEE_SUPPORT, EVENT_ID, now);

        tx().executeWithoutResult(status -> events.publishEvent(event));

        assertThat(
            jdbc.queryForObject(
                "select count(*) from support_case_audit where support_case_id = ? and action = 'CASE_OPENED' and event_id = ?",
                Integer.class,
                caseId,
                EVENT_ID
            )
        ).isEqualTo(1);
        assertThat(
            jdbc.queryForObject(
                "select count(*) from common_outbox where payload like ? and delivered_at is null",
                Integer.class,
                "%" + caseId + "%"
            )
        ).isEqualTo(1);
    }

    @Test
    void rollbackDoesNotPersistAuditOrNotificationOutboxRow() {
        Instant now = Instant.parse("2026-11-20T10:01:00Z");
        SupportCase supportCase = createCase(702L, now);
        SupportCaseOpenedEvent event = new SupportCaseOpenedEvent(caseId, 702L, SupportCaseType.ATTENDEE_SUPPORT, EVENT_ID, now);

        tx().executeWithoutResult(status -> {
            events.publishEvent(event);
            status.setRollbackOnly();
        });

        assertThat(
            jdbc.queryForObject("select count(*) from support_case_audit where support_case_id = ?", Integer.class, caseId)
        ).isZero();
        assertThat(
            jdbc.queryForObject("select count(*) from common_outbox where payload like ?", Integer.class, "%" + caseId + "%")
        ).isZero();
    }

    @Test
    void pendingNotificationSurvivesUntilDispatcherRunsAndIsThenAcknowledged() {
        Instant now = Instant.parse("2026-11-20T10:02:00Z");
        createCase(703L, now);
        when(iam.findVerifiedUserEmail(703L)).thenReturn(Optional.of(new VerifiedUserEmailView(703L, "requester@example.com")));
        when(eventManagement.supportRouting(EVENT_ID)).thenReturn(
            Optional.of(new EventManagementOhs.SupportRouting(EVENT_ID, 704L, List.of(705L)))
        );
        SupportCaseOpenedEvent event = new SupportCaseOpenedEvent(caseId, 703L, SupportCaseType.ATTENDEE_SUPPORT, EVENT_ID, now);

        tx().executeWithoutResult(status -> events.publishEvent(event));

        // Simulates the application being unavailable after commit: the event remains in the database.
        assertThat(
            jdbc.queryForObject(
                "select count(*) from common_outbox where payload like ? and delivered_at is null",
                Integer.class,
                "%" + caseId + "%"
            )
        ).isEqualTo(1);

        // Simulates a later process start / scheduled dispatcher run.
        outbox.dispatch();

        verify(emailSender).send(any(EmailSender.Email.class));
        assertThat(
            jdbc.queryForObject(
                "select count(*) from common_outbox where payload like ? and delivered_at is not null",
                Integer.class,
                "%" + caseId + "%"
            )
        ).isEqualTo(1);
    }
}
