package com.biletflow.biletflow.eventmanagement.application.cancellation;

import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CancellationNoticeWorker {

    private static final Logger LOG = LoggerFactory.getLogger(CancellationNoticeWorker.class);
    private final CancellationNoticeStore notices;
    private final CancellationNoticeSender sender;
    private final Clock clock;

    public CancellationNoticeWorker(CancellationNoticeStore notices, CancellationNoticeSender sender, Clock clock) {
        this.notices = Objects.requireNonNull(notices);
        this.sender = Objects.requireNonNull(sender);
        this.clock = Objects.requireNonNull(clock);
    }

    public void deliverPending() {
        for (int i = 0; i < 20; i++) {
            var next = notices.claim(clock.instant());
            if (next.isEmpty()) return;
            var claim = next.orElseThrow();
            try {
                sender.send(claim.notice());
                notices.complete(claim, clock.instant());
            } catch (RuntimeException failure) {
                notices.retry(claim, clock.instant());
                LOG.warn("Cancellation notice {} will be retried ({})", claim.notice().id(), failure.getClass().getSimpleName());
            }
        }
    }
}
