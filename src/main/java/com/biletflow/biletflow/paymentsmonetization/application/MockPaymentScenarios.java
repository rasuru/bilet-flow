package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.SettlementStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MockPaymentScenarios {
    enum Scenario {
        SUCCESS,
        FAILURE,
        DELAYED_SUCCESS,
        EXPIRATION,
    }

    record Attempt(Scenario scenario, Instant settlesAt, boolean refunded) {
        public SettlementStatus status(PaymentWork work, Instant now) {
            if (refunded) return SettlementStatus.REFUNDED;
            return switch (scenario) {
                case SUCCESS -> SettlementStatus.CAPTURED;
                case FAILURE -> SettlementStatus.VOIDED;
                case EXPIRATION -> SettlementStatus.EXPIRED;
                case DELAYED_SUCCESS -> {
                    if (!settlesAt.isBefore(work.getDeadline()) && !now.isBefore(work.getDeadline())) yield SettlementStatus.EXPIRED;
                    yield now.isBefore(settlesAt) ? SettlementStatus.PENDING : SettlementStatus.CAPTURED;
                }
            };
        }
    }

    String nextInvoice();
    Optional<Attempt> find(UUID paymentId);
    void insert(UUID paymentId, Scenario scenario, Instant settlesAt);
    void markRefunded(UUID paymentId);
}
