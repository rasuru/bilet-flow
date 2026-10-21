package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.iam.ohs.IamOhs;
import java.time.Clock;
import java.util.Objects;
import org.slf4j.*;
import org.springframework.stereotype.Service;

@Service
public class PayoutNoticeWorker {

    private static final Logger LOG = LoggerFactory.getLogger(PayoutNoticeWorker.class);
    private final PayoutNoticeStore notices;
    private final PayoutNoticeSender sender;
    private final IamOhs users;
    private final Clock clock;

    public PayoutNoticeWorker(PayoutNoticeStore notices, PayoutNoticeSender sender, IamOhs users, Clock clock) {
        this.notices = Objects.requireNonNull(notices);
        this.sender = Objects.requireNonNull(sender);
        this.users = Objects.requireNonNull(users);
        this.clock = Objects.requireNonNull(clock);
    }

    public void deliverPending() {
        for (int i = 0; i < 20; i++) {
            var next = notices.claim(clock.instant());
            if (next.isEmpty()) return;
            var claim = next.orElseThrow();
            try {
                var recipient = users.findVerifiedUserEmail(claim.organizerId());
                if (recipient.isEmpty()) {
                    notices.retry(claim, clock.instant());
                    continue;
                }
                sender.send(claim.payout(), recipient.orElseThrow().email());
                notices.complete(claim, clock.instant());
            } catch (RuntimeException failure) {
                notices.retry(claim, clock.instant());
                LOG.warn("Payout notice {} will be retried ({})", claim.payout().id(), failure.getClass().getSimpleName());
            }
        }
    }
}
