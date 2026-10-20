package com.biletflow.biletflow.paymentsmonetization.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentReconciler;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentWorkRepository;
import com.biletflow.biletflow.paymentsmonetization.config.PaymentPollingConfiguration;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

class PaymentPollingJobTest {

    @Test
    void aSlowPaymentDoesNotBlockAnotherPaymentOrCreateAnUnboundedQueue() throws Exception {
        var reconciler = mock(PaymentReconciler.class);
        var works = mock(PaymentWorkRepository.class);
        var slow = UUID.randomUUID();
        var fast = UUID.randomUUID();
        var neverQueued = UUID.randomUUID();
        var gate = new CountDownLatch(1);
        var bothStarted = new CountDownLatch(2);
        var fastFinished = new CountDownLatch(1);
        doAnswer(call -> {
            bothStarted.countDown();
            gate.await(5, TimeUnit.SECONDS);
            return null;
        })
            .when(reconciler)
            .reconcile(slow);
        doAnswer(call -> {
            bothStarted.countDown();
            fastFinished.countDown();
            gate.await(5, TimeUnit.SECONDS);
            return null;
        })
            .when(reconciler)
            .reconcile(fast);
        when(works.due(any())).thenReturn(List.of(slow, fast, neverQueued));
        var workers = new PaymentPollingConfiguration().paymentPollingExecutor(2);
        workers.initialize();
        try {
            var job = new PaymentPollingJob(reconciler, works, workers, Clock.systemUTC());
            job.poll();
            assertTrue(bothStarted.await(2, TimeUnit.SECONDS));
            assertTrue(fastFinished.await(2, TimeUnit.SECONDS), "The second status check must run while the first is blocked");
            job.poll();
            verify(reconciler, times(1)).reconcile(slow);
            verify(reconciler, never()).reconcile(neverQueued);
        } finally {
            gate.countDown();
            workers.shutdown();
        }
    }

    @Test
    void rejectedDispatchRemainsEligibleForTheNextSchedulingTick() {
        var reconciler = mock(PaymentReconciler.class);
        var works = mock(PaymentWorkRepository.class);
        var executor = mock(TaskExecutor.class);
        var id = UUID.randomUUID();
        when(works.due(any())).thenReturn(List.of(id));
        doThrow(new RejectedExecutionException())
            .doAnswer(call -> {
                call.getArgument(0, Runnable.class).run();
                return null;
            })
            .when(executor)
            .execute(any());
        var job = new PaymentPollingJob(reconciler, works, executor, Clock.systemUTC());
        job.poll();
        job.poll();
        verify(reconciler).reconcile(id);
    }
}
