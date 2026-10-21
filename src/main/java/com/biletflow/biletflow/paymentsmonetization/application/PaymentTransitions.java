package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.*;
import com.biletflow.biletflow.common.events.EventBus;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.*;
import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentOutcome;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Loads facts, invokes domain policy, and applies returned effects atomically. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentTransitions {

    private final PaymentWorkRepository works;
    private final ActivationRepository activations;
    private final PaymentLedger ledger;
    private final EventBus bus;
    private final FinancialAudit audit;
    private final TransactionLocks locks;
    private final EventManagementOhs events;
    private final Clock clock;

    public PaymentTransitions(
        PaymentWorkRepository works,
        ActivationRepository activations,
        PaymentLedger ledger,
        EventBus bus,
        FinancialAudit audit,
        TransactionLocks locks,
        EventManagementOhs events,
        Clock clock
    ) {
        this.works = Objects.requireNonNull(works, "works cannot be null");
        this.activations = Objects.requireNonNull(activations, "activations cannot be null");
        this.ledger = Objects.requireNonNull(ledger, "ledger cannot be null");
        this.bus = Objects.requireNonNull(bus, "bus cannot be null");
        this.audit = Objects.requireNonNull(audit, "audit cannot be null");
        this.locks = Objects.requireNonNull(locks, "locks cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    private PaymentWork locked(UUID id) {
        var initial = works.find(id).orElseThrow();
        // Global order: activation before payment, including concurrent activation replacement.
        if (!initial.isTicket()) locks.lock("activation:" + initial.getEventId());
        locks.lock("payment:" + id);
        return works.find(id).orElseThrow();
    }

    private PaymentSettlement.Context context(PaymentWork work, ActivationRepository.Activation activation, boolean inspectEvent) {
        var scope = inspectEvent ? events.salesScope(work.getEventId(), clock.instant()) : null;
        var facts =
            scope == null
                ? null
                : new PaymentSettlement.EventFacts(scope.registrationOpen(), scope.cancelled(), scope.organizerId(), scope.eventStartsAt());
        return new PaymentSettlement.Context(
            facts,
            activation == null ? null : activation.paymentId(),
            activation == null ? null : activation.aggregate(),
            activation != null && activation.verificationSimulated()
        );
    }

    public PaymentSettlement.Dispatch settle(UUID id, SettlementEvidence observation) {
        var work = locked(id);
        var activation = work.isTicket() ? null : activations.find(work.getEventId()).orElseThrow();
        var effects = PaymentSettlement.settle(work, observation, context(work, activation, true), clock.instant());
        persist(work, activation, effects);
        return effects.getDispatch();
    }

    public void resolve(UUID id, UUID expectedOperation, OperationResolution resolution) {
        var work = locked(id);
        var activation = work.isTicket() ? null : activations.find(work.getEventId()).orElseThrow();
        var effects = PaymentSettlement.resolve(work, expectedOperation, resolution, context(work, activation, false), clock.instant());
        persist(work, activation, effects);
    }

    private void persist(PaymentWork work, ActivationRepository.Activation activation, PaymentSettlement.Effects effects) {
        if (work.isTicket()) locks.lock("organizer-funds:" + work.getOrganizerId());
        if (effects.isCharge()) ledger.charge(work);
        if (effects.getRefundId() != null) ledger.refund(work, effects.getRefundId());
        for (var outcome : effects.getOutcomes())
            bus.publish(new PaymentOutcome(work.getId(), work.getOrderId(), outcome.status(), outcome.refundId()).event());
        if (effects.isActivationChanged()) activations.save(activation);
        works.save(work);
        for (var action : effects.getAudit()) audit.record("payments", work.getId(), null, action);
    }
}
