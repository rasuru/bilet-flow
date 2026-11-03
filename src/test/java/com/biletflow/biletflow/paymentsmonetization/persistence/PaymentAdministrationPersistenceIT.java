package com.biletflow.biletflow.paymentsmonetization.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.paymentsmonetization.application.*;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivation;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivationStatus;
import com.biletflow.biletflow.paymentsmonetization.domain.notice.PaymentNoticeKind;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Administrator search filters, the durable notice queue and the settings row against PostgreSQL. */
@IntegrationTest
class PaymentAdministrationPersistenceIT {

    @Autowired
    PaymentWorkRepository works;

    @Autowired
    ActivationRepository activations;

    @Autowired
    PaymentAdminQueries adminQueries;

    @Autowired
    PaymentNoticeStore notices;

    @Autowired
    PaymentSettingsRepository settings;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    EntityManager entities;

    private final long organizer = ThreadLocalRandom.current().nextLong(100000, Long.MAX_VALUE);
    private final Instant now = Instant.now();
    private final PaymentTerms terms = new PaymentTerms(new BigDecimal("1000"), new BigDecimal("0.03"));

    private TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }

    private PaymentWork charged(UUID event, String amount) {
        return tx().execute(status -> {
            var work = terms.ticket(
                UUID.randomUUID(),
                event,
                organizer,
                organizer,
                new BigDecimal(amount),
                now.plusSeconds(600),
                now,
                works.nextInvoice(),
                "test-secret"
            );
            work.getPayment().reconcileCharge("sandbox-" + work.getId(), now);
            works.insert(work);
            return work;
        });
    }

    @AfterEach
    void cleanup() {
        tx().executeWithoutResult(status -> {
            entities.createQuery("delete PaymentNoticeJpaEntity n where n.recipientId = :o").setParameter("o", organizer).executeUpdate();
            entities.createQuery("delete PaymentSettingJpaEntity s").executeUpdate();
            entities.createQuery("delete ActivationJpaEntity a where a.organizerId = :o").setParameter("o", organizer).executeUpdate();
            entities.createQuery("delete PaymentWorkJpaEntity w where w.organizerId = :o").setParameter("o", organizer).executeUpdate();
        });
    }

    @Test
    void administratorSearchFiltersPaymentsAndActivations() {
        UUID event = UUID.randomUUID();
        var paid = charged(event, "1000.00");
        tx().executeWithoutResult(status -> {
            var fee = terms.activation(event, organizer, now, works.nextInvoice(), "test-secret");
            works.insert(fee);
            activations.save(
                new ActivationRepository.Activation(EventPaidSalesActivation.request(event, organizer, now), fee.getId(), true, "test")
            );
        });

        var tickets = adminQueries.findPaymentIds(new PaymentAdminQueries.PaymentFilter(null, PaymentPurpose.TICKET, event, null), 0, 10);
        assertEquals(List.of(paid.getId()), tickets);
        var succeeded = adminQueries.findPaymentIds(
            new PaymentAdminQueries.PaymentFilter(PaymentStatus.SUCCEEDED, null, event, organizer),
            0,
            10
        );
        assertEquals(List.of(paid.getId()), succeeded);
        assertTrue(
            adminQueries.findPaymentIds(new PaymentAdminQueries.PaymentFilter(PaymentStatus.FAILED, null, event, null), 0, 10).isEmpty()
        );
        assertEquals(2, adminQueries.findPaymentIds(new PaymentAdminQueries.PaymentFilter(null, null, event, null), 0, 10).size());

        var pending = adminQueries.findActivations(EventPaidSalesActivationStatus.PENDING, 0, 100);
        assertTrue(pending.stream().anyMatch(a -> a.aggregate().getEventId().equals(event)));
        assertTrue(
            adminQueries
                .findActivations(EventPaidSalesActivationStatus.ACTIVE, 0, 100)
                .stream()
                .noneMatch(a -> a.aggregate().getEventId().equals(event))
        );
    }

    @Test
    void noticesAreDeduplicatedLeasedAndAcknowledgedOnce() {
        // A fixed early instant isolates this test's rows from notices other tests leave behind.
        Instant created = Instant.parse("2000-01-01T00:00:00Z");
        UUID payment = UUID.randomUUID();
        var notice = new PaymentNoticeStore.PaymentNotice(
            PaymentNoticeStore.PaymentNotice.idFor(payment, PaymentNoticeKind.TICKET_PAYMENT_FAILED),
            PaymentNoticeKind.TICKET_PAYMENT_FAILED,
            organizer,
            UUID.randomUUID(),
            payment,
            new BigDecimal("1000.00"),
            created
        );
        tx().executeWithoutResult(status -> {
            notices.enqueue(notice);
            notices.enqueue(notice);
        });

        var claim = notices.claim(created.plusSeconds(1)).orElseThrow();
        assertEquals(notice, claim.notice());
        assertTrue(notices.claim(created.plusSeconds(2)).isEmpty(), "A leased notice is not claimed twice");

        notices.retry(claim, created.plusSeconds(3));
        assertTrue(notices.claim(created.plusSeconds(4)).isEmpty(), "A retried notice waits for its backoff");
        var second = notices.claim(created.plusSeconds(40)).orElseThrow();
        notices.complete(claim, created.plusSeconds(41));
        assertTrue(notices.claim(created.plusSeconds(100)).isEmpty(), "Still leased: the stale first claim cannot complete it");
        notices.complete(second, created.plusSeconds(42));
        assertTrue(notices.claim(created.plusSeconds(10000)).isEmpty(), "A sent notice is never claimed again");
        assertEquals(
            Long.valueOf(1),
            entities
                .createQuery("select count(n) from PaymentNoticeJpaEntity n where n.recipientId = :o", Long.class)
                .setParameter("o", organizer)
                .getSingleResult()
        );
    }

    @Test
    void activationFeeSettingIsCreatedThenUpdated() {
        assertTrue(settings.activationFee().isEmpty());
        tx().executeWithoutResult(status ->
            settings.saveActivationFee(new PaymentSettingsRepository.ActivationFeeSetting(new BigDecimal("1500.00"), organizer, now))
        );
        tx().executeWithoutResult(status ->
            settings.saveActivationFee(new PaymentSettingsRepository.ActivationFeeSetting(new BigDecimal("2000.00"), organizer, now))
        );
        assertEquals(0, new BigDecimal("2000.00").compareTo(settings.activationFee().orElseThrow().amountKzt()));
    }
}
