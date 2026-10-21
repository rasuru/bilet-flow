package com.biletflow.biletflow.paymentsmonetization.infrastructure.payout;

import com.biletflow.biletflow.paymentsmonetization.application.PayoutNoticeWorker;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "biletflow.payments.payout-mail-enabled", havingValue = "true", matchIfMissing = true)
public class PayoutNoticeJob {

    private final PayoutNoticeWorker worker;

    public PayoutNoticeJob(PayoutNoticeWorker worker) {
        this.worker = Objects.requireNonNull(worker);
    }

    @Scheduled(fixedDelayString = "${biletflow.payments.payout-mail-delay-ms:5000}")
    public void deliver() {
        worker.deliverPending();
    }
}
