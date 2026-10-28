package com.biletflow.biletflow.paymentsmonetization.infrastructure.notice;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentNoticeWorker;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "biletflow.payments.notice-mail-enabled", havingValue = "true", matchIfMissing = true)
public class PaymentNoticeJob {

    private final PaymentNoticeWorker worker;

    public PaymentNoticeJob(PaymentNoticeWorker worker) {
        this.worker = Objects.requireNonNull(worker);
    }

    @Scheduled(fixedDelayString = "${biletflow.payments.notice-mail-delay-ms:5000}")
    public void deliver() {
        worker.deliverPending();
    }
}
