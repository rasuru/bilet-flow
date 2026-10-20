package com.biletflow.biletflow.ordercheckout.infrastructure.refund;

import com.biletflow.biletflow.ordercheckout.application.refund.RefundNoticeWorker;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "biletflow.checkout.refund-mail-enabled", havingValue = "true", matchIfMissing = true)
public class RefundNoticeJob {

    private final RefundNoticeWorker worker;

    public RefundNoticeJob(RefundNoticeWorker worker) {
        this.worker = Objects.requireNonNull(worker);
    }

    @Scheduled(
        fixedDelayString = "${biletflow.checkout.refund-mail-delay-ms:5000}",
        initialDelayString = "${biletflow.checkout.refund-mail-initial-delay-ms:10000}"
    )
    public void deliver() {
        worker.deliverPending();
    }
}
