package com.biletflow.biletflow.ordercheckout.infrastructure;

import com.biletflow.biletflow.ordercheckout.application.*;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CheckoutExpiryJob {

    private static final Logger LOG = LoggerFactory.getLogger(CheckoutExpiryJob.class);
    private final CheckoutProcessRepository processes;
    private final CheckoutService service;

    public CheckoutExpiryJob(CheckoutProcessRepository processes, CheckoutService service) {
        this.processes = Objects.requireNonNull(processes, "processes cannot be null");
        this.service = Objects.requireNonNull(service, "service cannot be null");
    }

    @Scheduled(fixedDelayString = "${biletflow.payments.expiry-delay-ms:5000}")
    public void expire() {
        for (var id : processes.active())
            try {
                service.expire(id);
            } catch (Exception e) {
                LOG.warn("Checkout {} expiry will be retried ({})", id, e.getClass().getSimpleName());
            }
    }
}
