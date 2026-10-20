package com.biletflow.biletflow.eventmanagement.infrastructure.cancellation;

import com.biletflow.biletflow.eventmanagement.application.cancellation.CancellationNoticeWorker;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "biletflow.events.cancellation-mail-enabled", havingValue = "true", matchIfMissing = true)
public class CancellationNoticeJob {

    private final CancellationNoticeWorker worker;

    public CancellationNoticeJob(CancellationNoticeWorker worker) {
        this.worker = Objects.requireNonNull(worker);
    }

    @Scheduled(
        fixedDelayString = "${biletflow.events.cancellation-mail-delay-ms:5000}",
        initialDelayString = "${biletflow.events.cancellation-mail-initial-delay-ms:10000}"
    )
    public void deliver() {
        worker.deliverPending();
    }
}
