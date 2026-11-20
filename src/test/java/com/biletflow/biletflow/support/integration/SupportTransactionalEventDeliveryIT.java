package com.biletflow.biletflow.support.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.common.events.JdbcEventBus;
import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import com.biletflow.biletflow.support.application.event.SupportCaseOpenedEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class SupportTransactionalEventDeliveryIT {

    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JdbcEventBus outbox;

    @MockitoBean private IamOhs iam;
    @MockitoBean private EmailSender emailSender;

    private final UUID caseId = UUID.randomUUID();

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from common_outbox where id in (select id from common_outbox where payload like ?)",
            "%" + caseId + "%");
        jdbc.update("delete from common_financial_audit where aggregate_id = ?", caseId);
    }

    @Test
    void beforeCommitListenersWriteAuditAndDurableNotificationInSameTransaction() {
        SupportCaseOpenedEvent event = new SupportCaseOpenedEvent(caseId, 701L, Instant.parse("2026-11-20T10:00:00Z"));

        tx().executeWithoutResult(status -> events.publishEvent(event));

        assertThat(jdbc.queryForObject(
            "select count(*) from common_financial_audit where aggregate_id = ? and action = 'CASE_OPENED'",
            Integer.class, caseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from common_outbox where payload like ? and delivered_at is null",
            Integer.class, "%" + caseId + "%")).isEqualTo(1);
    }

    @Test
    void rollbackDoesNotPersistAuditOrNotificationOutboxRow() {
        SupportCaseOpenedEvent event = new SupportCaseOpenedEvent(caseId, 702L, Instant.parse("2026-11-20T10:01:00Z"));

        tx().executeWithoutResult(status -> {
            events.publishEvent(event);
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject(
            "select count(*) from common_financial_audit where aggregate_id = ?", Integer.class, caseId)).isZero();
        assertThat(jdbc.queryForObject(
            "select count(*) from common_outbox where payload like ?", Integer.class, "%" + caseId + "%")).isZero();
    }

    @Test
    void pendingNotificationSurvivesUntilDispatcherRunsAndIsThenAcknowledged() {
        when(iam.findVerifiedUserEmail(703L)).thenReturn(Optional.of(new VerifiedUserEmailView(703L, "requester@example.com")));
        SupportCaseOpenedEvent event = new SupportCaseOpenedEvent(caseId, 703L, Instant.parse("2026-11-20T10:02:00Z"));

        tx().executeWithoutResult(status -> events.publishEvent(event));

        // Simulates the application being unavailable after commit: the event remains in the database.
        assertThat(jdbc.queryForObject(
            "select count(*) from common_outbox where payload like ? and delivered_at is null",
            Integer.class, "%" + caseId + "%")).isEqualTo(1);

        // Simulates a later process start / scheduled dispatcher run.
        outbox.dispatch();

        verify(emailSender).send(any(EmailSender.Email.class));
        assertThat(jdbc.queryForObject(
            "select count(*) from common_outbox where payload like ? and delivered_at is not null",
            Integer.class, "%" + caseId + "%")).isEqualTo(1);
    }
}
