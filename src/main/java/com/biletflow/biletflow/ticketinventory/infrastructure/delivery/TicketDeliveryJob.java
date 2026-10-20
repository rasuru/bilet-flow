package com.biletflow.biletflow.ticketinventory.infrastructure.delivery;

import com.biletflow.biletflow.ticketinventory.application.delivery.TicketDeliveryWorker;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "biletflow.tickets.delivery-enabled", havingValue = "true", matchIfMissing = true)
public class TicketDeliveryJob {

    private final TicketDeliveryWorker worker;

    public TicketDeliveryJob(TicketDeliveryWorker worker) {
        this.worker = Objects.requireNonNull(worker);
    }

    @Scheduled(
        fixedDelayString = "${biletflow.tickets.delivery-delay-ms:5000}",
        initialDelayString = "${biletflow.tickets.delivery-initial-delay-ms:10000}"
    )
    public void deliver() {
        worker.deliverPending();
    }
}
