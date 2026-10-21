package com.biletflow.biletflow.paymentsmonetization.infrastructure.payout;

import com.biletflow.biletflow.paymentsmonetization.application.PayoutService;
import com.biletflow.biletflow.paymentsmonetization.domain.payout.PayoutRepository;
import java.util.Objects;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "biletflow.payments.payout-enabled", havingValue = "true", matchIfMissing = true)
public class PayoutProcessingJob {

    private static final Logger LOG = LoggerFactory.getLogger(PayoutProcessingJob.class);
    private final PayoutRepository payouts;
    private final PayoutService service;
    private final boolean simulateFailure;

    public PayoutProcessingJob(
        PayoutRepository payouts,
        PayoutService service,
        @Value("${biletflow.payments.payout-simulate-failure:false}") boolean simulateFailure
    ) {
        this.payouts = Objects.requireNonNull(payouts);
        this.service = Objects.requireNonNull(service);
        this.simulateFailure = simulateFailure;
    }

    @Scheduled(fixedDelayString = "${biletflow.payments.payout-delay-ms:1000}")
    public void process() {
        for (var id : payouts.pending(20)) {
            try {
                service.process(id, !simulateFailure);
            } catch (RuntimeException failure) {
                LOG.warn("Payout {} processing deferred ({})", id.value(), failure.getClass().getSimpleName());
            }
        }
    }
}
