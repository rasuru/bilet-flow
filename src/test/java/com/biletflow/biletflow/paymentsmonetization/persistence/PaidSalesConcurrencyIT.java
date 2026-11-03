package com.biletflow.biletflow.paymentsmonetization.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.paymentsmonetization.application.*;
import com.biletflow.biletflow.paymentsmonetization.application.command.SuspendPaidSalesCommand;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivation;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivationStatus;
import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.ActivationFeePaymentId;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentTerms;
import com.biletflow.biletflow.paymentsmonetization.ohs.dto.StartPaymentCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
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
class PaidSalesConcurrencyIT {

    @MockitoBean
    EventManagementOhs events;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    CurrentActor actor;

    @Autowired
    PaymentService payments;

    @Autowired
    PaidSalesAdministrationService paidSales;

    @Autowired
    PaymentSettingsService paymentSettings;

    @Autowired
    PaymentSettingsRepository settings;

    @Autowired
    PaymentWorkRepository works;

    @Autowired
    ActivationRepository activations;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    JdbcTemplate jdbc;

    private final long organizer = ThreadLocalRandom.current().nextLong(100000, Long.MAX_VALUE);
    private final UUID event = UUID.randomUUID();
    private final Instant now = Instant.now();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    private TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }

    @BeforeEach
    void activeEvent() {
        doReturn(Optional.of(99L)).when(actor).userId();
        doReturn(true).when(actor).isAdministrator();
        when(events.salesScope(eq(event), any())).thenReturn(
            new EventManagementOhs.SalesScope(event, organizer, true, false, now.plusSeconds(86400))
        );
        tx().executeWithoutResult(status -> {
            var fee = new PaymentTerms(new BigDecimal("1000"), BigDecimal.ZERO).activation(
                event,
                organizer,
                now,
                works.nextInvoice(),
                "secret"
            );
            works.insert(fee);
            activations.save(
                new ActivationRepository.Activation(
                    new EventPaidSalesActivation(
                        event,
                        organizer,
                        now,
                        EventPaidSalesActivationStatus.ACTIVE,
                        new ActivationFeePaymentId(fee.getId()),
                        null,
                        null
                    ),
                    fee.getId(),
                    true,
                    "test"
                )
            );
        });
    }

    @AfterEach
    void cleanup() {
        pool.shutdownNow();
        tx().executeWithoutResult(status -> {
            jdbc.update(
                "delete from common_financial_audit where aggregate_id = ? or actor_id = 99 or aggregate_id in (select id from pm_payment_work where event_id = ?)",
                event,
                event
            );
            jdbc.update("delete from pm_event_activation where event_id = ?", event);
            jdbc.update("delete from pm_payment_work where event_id = ?", event);
            jdbc.update("delete from pm_setting");
        });
    }

    private StartPaymentCommand checkout() {
        return new StartPaymentCommand(UUID.randomUUID(), event, 1, new BigDecimal("5000.00"), now.plusSeconds(600));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void suspensionWaitsForACheckoutThatAlreadyPassedTheActivationCheck() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var order = checkout();
        var checkout = pool.submit(() ->
            tx().execute(status -> {
                var payment = payments.initiate(order);
                started.countDown();
                await(release);
                return payment;
            })
        );
        await(started);

        var suspension = pool.submit(() -> paidSales.suspend(new SuspendPaidSalesCommand(event, "Fraud investigation")));
        Thread.sleep(500);
        assertFalse(suspension.isDone(), "Suspension must wait for the checkout holding the activation lock");

        release.countDown();
        assertNotNull(checkout.get(10, TimeUnit.SECONDS));
        assertEquals("SUSPENDED", suspension.get(10, TimeUnit.SECONDS).status());
        assertTrue(works.findOrder(order.orderId()).isPresent(), "The checkout that won the lock keeps its payment");
    }

    @Test
    void checkoutStartedDuringASuspensionSeesItAndCreatesNoPayment() throws Exception {
        var suspended = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var suspension = pool.submit(() ->
            tx().execute(status -> {
                var view = paidSales.suspend(new SuspendPaidSalesCommand(event, "Fraud investigation"));
                suspended.countDown();
                await(release);
                return view;
            })
        );
        await(suspended);

        var order = checkout();
        var checkout = pool.submit(() -> payments.initiate(order));
        Thread.sleep(500);
        assertFalse(checkout.isDone(), "Checkout must wait for the suspension holding the activation lock");

        release.countDown();
        assertEquals("SUSPENDED", suspension.get(10, TimeUnit.SECONDS).status());
        var failure = assertThrows(ExecutionException.class, () -> checkout.get(10, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(works.findOrder(order.orderId()).isEmpty(), "No payment is created after the suspension");
    }

    @Test
    void simultaneousFeeChangesBothSucceedAndTheLastOneWins() throws Exception {
        for (int round = 0; round < 5; round++) {
            var gate = new CountDownLatch(1);
            String first = "1" + round + "00.00",
                second = "2" + round + "00.00";
            Callable<String> a = () -> {
                await(gate);
                return paymentSettings.changeActivationFee(new BigDecimal(first)).amountKzt().toPlainString();
            };
            Callable<String> b = () -> {
                await(gate);
                return paymentSettings.changeActivationFee(new BigDecimal(second)).amountKzt().toPlainString();
            };
            var fa = pool.submit(a);
            var fb = pool.submit(b);
            gate.countDown();
            fa.get(10, TimeUnit.SECONDS);
            fb.get(10, TimeUnit.SECONDS);

            var stored = settings.activationFee().orElseThrow().amountKzt().toPlainString();
            assertTrue(Set.of(first, second).contains(stored), "The stored fee is one of the two changes, not a stale value");
        }
        assertEquals(
            10,
            jdbc.queryForObject(
                "select count(*) from common_financial_audit where action = 'ACTIVATION_FEE_CHANGED' and actor_id = 99",
                Integer.class
            ),
            "Every change is applied and audited"
        );
    }
}
