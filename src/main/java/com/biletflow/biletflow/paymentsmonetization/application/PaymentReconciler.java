package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.*;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.*;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Coordinates durable claims and domain transitions; network calls run outside transactions. */
@Service
public class PaymentReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentReconciler.class);
    private final PaymentWorkRepository works;
    private final PaymentGateway bank;
    private final PaymentTransitions transitions;
    private final TransactionLocks locks;
    private final Clock clock;
    private final Transactions tx;

    public PaymentReconciler(
        PaymentWorkRepository works,
        PaymentGateway bank,
        PaymentTransitions transitions,
        TransactionLocks locks,
        Clock clock,
        Transactions tx
    ) {
        this.works = Objects.requireNonNull(works, "works cannot be null");
        this.bank = Objects.requireNonNull(bank, "bank cannot be null");
        this.transitions = Objects.requireNonNull(transitions, "transitions cannot be null");
        this.locks = Objects.requireNonNull(locks, "locks cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.tx = Objects.requireNonNull(tx, "tx cannot be null");
    }

    public void poll() {
        for (UUID id : works.due(clock.instant())) reconcile(id);
    }

    public void reconcile(UUID id) {
        if (!tx.required(() -> works.lease(id, clock.instant(), clock.instant().plusSeconds(90)))) return;
        try {
            var snapshot = works.find(id).orElseThrow();
            var observation = bank.inspect(snapshot.getInvoiceId());
            var command = tx.required(() -> transitions.settle(id, observation));
            if (command != null) {
                // Domain dispatch intent has committed. An ambiguous send is never automatically repeated.
                try {
                    bank.mutate(command.transactionId(), command.operation(), command.operationId().toString());
                    tx.run(() -> {
                        locks.lock("payment:" + id);
                        var latest = works.find(id).orElseThrow();
                        latest.acknowledge(command.operationId(), command.operation());
                        works.save(latest);
                    });
                } catch (Exception unknown) {
                    LOG.warn("Bank mutation outcome requires reconciliation for payment {}", id);
                }
            }
        } catch (Exception failure) {
            LOG.warn("Payment {} reconciliation deferred ({})", id, failure.getClass().getSimpleName());
        } finally {
            tx.run(() -> {
                var work = works.find(id).orElseThrow();
                works.schedule(id, clock.instant().plusSeconds(work.pollDelaySeconds(clock.instant())));
            });
        }
    }
}
