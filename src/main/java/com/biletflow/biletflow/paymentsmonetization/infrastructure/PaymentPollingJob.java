package com.biletflow.biletflow.paymentsmonetization.infrastructure;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentReconciler;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentWorkRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduling and bounded parallelism live here; each worker still acquires its durable database lease. */
@Component
@ConditionalOnProperty(name = "biletflow.payments.poll-enabled", havingValue = "true", matchIfMissing = true)
public class PaymentPollingJob {

    private final PaymentReconciler reconciler;
    private final PaymentWorkRepository works;
    private final TaskExecutor workers;
    private final Clock clock;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public PaymentPollingJob(
        PaymentReconciler reconciler,
        PaymentWorkRepository works,
        @Qualifier("paymentPollingExecutor") TaskExecutor workers,
        Clock clock
    ) {
        this.reconciler = Objects.requireNonNull(reconciler);
        this.works = Objects.requireNonNull(works);
        this.workers = Objects.requireNonNull(workers);
        this.clock = Objects.requireNonNull(clock);
    }

    @Scheduled(fixedDelayString = "${biletflow.payments.poll-delay-ms:1000}")
    public void poll() {
        for (UUID id : works.due(clock.instant())) {
            if (!inFlight.add(id)) continue;
            try {
                workers.execute(() -> {
                    try {
                        reconciler.reconcile(id);
                    } finally {
                        inFlight.remove(id);
                    }
                });
            } catch (RejectedExecutionException busy) {
                inFlight.remove(id);
                // No queue of stale snapshots: the next tick reads the current priority order.
                return;
            }
        }
    }
}
