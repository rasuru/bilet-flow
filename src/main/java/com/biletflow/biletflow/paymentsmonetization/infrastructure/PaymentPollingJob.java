package com.biletflow.biletflow.paymentsmonetization.infrastructure;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentReconciler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentPollingJob {

    private final PaymentReconciler reconciler;

    public PaymentPollingJob(PaymentReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @Scheduled(fixedDelayString = "${biletflow.payments.poll-delay-ms:5000}")
    public void poll() {
        reconciler.poll();
    }
}
