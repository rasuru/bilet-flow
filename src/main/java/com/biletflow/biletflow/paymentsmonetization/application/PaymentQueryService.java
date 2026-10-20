package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.paymentsmonetization.application.view.*;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final ActivationRepository activations;
    private final PaymentLedger ledger;
    private final PaymentService payments;

    public PaymentQueryService(ActivationRepository activations, PaymentLedger ledger, PaymentService payments) {
        this.activations = Objects.requireNonNull(activations);
        this.ledger = Objects.requireNonNull(ledger);
        this.payments = Objects.requireNonNull(payments);
    }

    public ActivationView activation(UUID eventId, long actorId) {
        var activation = activations.find(eventId).orElseThrow(() -> new EntityNotFoundException("Activation not found"));
        payments.authorized(activation.paymentId(), actorId);
        return new ActivationView(
            eventId,
            activation.aggregate().getStatus().name(),
            activation.paymentId(),
            true,
            activation.verificationSimulated()
        );
    }

    public RevenueView revenue(long organizerId) {
        return new RevenueView(true, true, "KZT", ledger.summary(organizerId));
    }
}
