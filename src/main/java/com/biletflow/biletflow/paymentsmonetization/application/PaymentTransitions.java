package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.*;
import com.biletflow.biletflow.common.events.EventBus;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.*;
import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentOutcome;
import java.time.Clock;
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
        this.works = works;
        this.activations = activations;
        this.ledger = ledger;
        this.bus = bus;
        this.audit = audit;
        this.locks = locks;
        this.events = events;
        this.clock = clock;
    }

    private PaymentWork locked(UUID id) {
        var initial = works.find(id).orElseThrow();
        // Global order: activation before payment, including concurrent activation replacement.
        if (!initial.isTicket()) locks.lock("activation:" + initial.eventId);
        locks.lock("payment:" + id);
        return works.find(id).orElseThrow();
    }

    private PaymentSettlement.Context context(PaymentWork work, ActivationRepository.Activation activation, boolean inspectEvent) {
        var scope = inspectEvent ? events.salesScope(work.eventId, clock.instant()) : null;
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
        var activation = work.isTicket() ? null : activations.find(work.eventId).orElseThrow();
        var effects = PaymentSettlement.settle(work, observation, context(work, activation, true), clock.instant());
        persist(work, activation, effects);
        return effects.dispatch;
    }

    public void resolve(UUID id, UUID expectedOperation, String resolution) {
        var work = locked(id);
        var activation = work.isTicket() ? null : activations.find(work.eventId).orElseThrow();
        var effects = PaymentSettlement.resolve(work, expectedOperation, resolution, context(work, activation, false), clock.instant());
        persist(work, activation, effects);
    }

    private void persist(PaymentWork work, ActivationRepository.Activation activation, PaymentSettlement.Effects effects) {
        if (effects.charge) ledger.charge(work);
        if (effects.refundId != null) ledger.refund(work, effects.refundId);
        for (var outcome : effects.outcomes)
            bus.publish(new PaymentOutcome(work.id, work.orderId, outcome.status(), outcome.refundId()).event());
        if (effects.activationChanged) activations.save(activation);
        works.save(work);
        for (var action : effects.audit) audit.record("payments", work.id, null, action);
    }
}
