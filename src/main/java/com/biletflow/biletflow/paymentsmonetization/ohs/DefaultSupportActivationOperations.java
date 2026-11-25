package com.biletflow.biletflow.paymentsmonetization.ohs;

import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.paymentsmonetization.application.ActivationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultSupportActivationOperations implements SupportActivationOperations {
    private final ActivationRepository activations;
    private final PaymentsOhs payments;
    private final CurrentActor actor;

    public DefaultSupportActivationOperations(ActivationRepository activations, PaymentsOhs payments, CurrentActor actor) {
        this.activations = activations;
        this.payments = payments;
        this.actor = actor;
    }

    @Override
    public Optional<Activation> inspect(UUID eventId) {
        actor.requireAdministrator();
        return activations.find(eventId).map(a -> new Activation(a.aggregate().getStatus().name(), payments.get(a.paymentId())));
    }
}

