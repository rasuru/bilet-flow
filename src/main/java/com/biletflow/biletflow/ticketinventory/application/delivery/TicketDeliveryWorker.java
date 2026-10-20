package com.biletflow.biletflow.ticketinventory.application.delivery;

import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus;
import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TicketDeliveryWorker {

    private static final Logger LOG = LoggerFactory.getLogger(TicketDeliveryWorker.class);
    private final TicketDeliveryStore deliveries;
    private final TicketDocumentService documents;
    private final TicketDeliverySender sender;
    private final Clock clock;

    public TicketDeliveryWorker(TicketDeliveryStore deliveries, TicketDocumentService documents, TicketDeliverySender sender, Clock clock) {
        this.deliveries = Objects.requireNonNull(deliveries);
        this.documents = Objects.requireNonNull(documents);
        this.sender = Objects.requireNonNull(sender);
        this.clock = Objects.requireNonNull(clock);
    }

    public void deliverPending() {
        for (int i = 0; i < 20; i++) {
            var next = deliveries.claim(clock.instant());
            if (next.isEmpty()) return;
            var claim = next.orElseThrow();
            try {
                var document = documents.forDelivery(claim.ticketId());
                if (!document.event().cancelled() && document.ticket().status() == TicketStatus.REFUND_PENDING) {
                    // Refund rejection can restore VALID; defer rather than permanently suppress delivery.
                    deliveries.retry(claim, clock.instant());
                    continue;
                }
                boolean skip = document.event().cancelled() || document.ticket().status() != TicketStatus.VALID;
                if (!skip) sender.send(document, claim.messageId());
                deliveries.complete(claim, skip, clock.instant());
            } catch (RuntimeException failure) {
                deliveries.retry(claim, clock.instant());
                LOG.warn("Ticket delivery {} will be retried ({})", claim.ticketId(), failure.getClass().getSimpleName());
            }
        }
    }
}
