package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends queued payment and activation emails outside the financial transaction. Delivery is at least once; a failure
 * retries later and never changes the payment or activation it describes.
 */
@Service
public class PaymentNoticeWorker {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentNoticeWorker.class);
    private final PaymentNoticeStore notices;
    private final PaymentNoticeSender sender;
    private final IamOhs users;
    private final EventManagementOhs events;
    private final Clock clock;

    public PaymentNoticeWorker(
        PaymentNoticeStore notices,
        PaymentNoticeSender sender,
        IamOhs users,
        EventManagementOhs events,
        Clock clock
    ) {
        this.notices = Objects.requireNonNull(notices);
        this.sender = Objects.requireNonNull(sender);
        this.users = Objects.requireNonNull(users);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
    }

    public void deliverPending() {
        for (int i = 0; i < 20; i++) {
            var next = notices.claim(clock.instant());
            if (next.isEmpty()) return;
            var claim = next.orElseThrow();
            var notice = claim.notice();
            try {
                var recipient = users.findVerifiedUserEmail(notice.recipientId());
                if (recipient.isEmpty()) {
                    notices.retry(claim, clock.instant());
                    continue;
                }
                sender.send(notice, eventTitle(notice), recipient.orElseThrow().email());
                notices.complete(claim, clock.instant());
            } catch (RuntimeException failure) {
                notices.retry(claim, clock.instant());
                LOG.warn("Payment notice {} ({}) will be retried ({})", notice.id(), notice.kind(), failure.getClass().getSimpleName());
            }
        }
    }

    /** The title is a courtesy: an event that cannot be displayed must not block a financial notice forever. */
    private String eventTitle(PaymentNoticeStore.PaymentNotice notice) {
        try {
            var title = events.ticketDetails(notice.eventId(), null).title();
            return title == null || title.isBlank() ? "your event" : title;
        } catch (RuntimeException unavailable) {
            return "your event";
        }
    }
}
