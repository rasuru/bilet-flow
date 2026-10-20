package com.biletflow.biletflow.ordercheckout.application.refund;

import com.biletflow.biletflow.iam.ohs.IamOhs;
import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RefundNoticeWorker {

    private static final Logger LOG = LoggerFactory.getLogger(RefundNoticeWorker.class);
    private final RefundNoticeStore notices;
    private final RefundNoticeSender sender;
    private final IamOhs users;
    private final Clock clock;

    public RefundNoticeWorker(RefundNoticeStore notices, RefundNoticeSender sender, IamOhs users, Clock clock) {
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
                var recipient = users.findVerifiedUserEmail(claim.notice().purchaserId());
                if (recipient.isEmpty()) {
                    notices.retry(claim, clock.instant());
                    continue;
                }
                sender.send(claim.notice(), recipient.orElseThrow().email());
                notices.complete(claim, clock.instant());
            } catch (RuntimeException failure) {
                notices.retry(claim, clock.instant());
                LOG.warn("Refund notice {} will be retried ({})", claim.notice().id(), failure.getClass().getSimpleName());
            }
        }
    }
}
