package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.CallbackSecrets;
import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivation;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentTerms;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import com.biletflow.biletflow.paymentsmonetization.ohs.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService implements PaymentsOhs {

    private final PaymentWorkRepository works;
    private final ActivationRepository activations;
    private final EventManagementOhs events;
    private final TransactionLocks locks;
    private final FinancialAudit audit;
    private final Clock clock;
    private final PaymentTerms terms;
    private final PaymentGateway bank;
    private final CallbackSecrets secrets;

    public PaymentService(
        PaymentWorkRepository works,
        ActivationRepository activations,
        EventManagementOhs events,
        TransactionLocks locks,
        FinancialAudit audit,
        Clock clock,
        PaymentTerms terms,
        PaymentGateway bank,
        CallbackSecrets secrets
    ) {
        this.works = works;
        this.activations = activations;
        this.events = events;
        this.locks = locks;
        this.audit = audit;
        this.clock = clock;
        this.terms = terms;
        this.bank = bank;
        this.secrets = secrets;
    }

    @Override
    public PaymentView initiate(Start c) {
        Objects.requireNonNull(c);
        Objects.requireNonNull(c.orderId());
        Objects.requireNonNull(c.eventId());
        terms.requireTicketRequest(c.payerId(), c.amount(), c.expiresAt(), clock.instant());
        bank.validateAmount(c.amount());
        locks.lock("payment-order:" + c.orderId());
        var existing = works.findOrder(c.orderId());
        if (existing.isPresent()) {
            var w = existing.orElseThrow();
            w.requireSameRequest(c.eventId(), c.payerId(), c.amount());
            return view(w);
        }
        var scope = events.salesScope(c.eventId(), clock.instant());
        var activation = activations.find(c.eventId()).orElseThrow(() -> new IllegalStateException("Paid sales are not activated"));
        terms.requirePaidSales(scope.registrationOpen(), activation.aggregate().permitsPaidSales());
        PaymentWork w = terms.ticket(
            c.orderId(),
            c.eventId(),
            c.payerId(),
            scope.organizerId(),
            c.amount(),
            c.expiresAt(),
            clock.instant(),
            works.nextInvoice(),
            secrets.generate()
        );
        works.insert(w);
        audit.record("payments", w.id, c.payerId(), "PAYMENT_INITIATED_SANDBOX");
        return view(w);
    }

    /** Explicitly labelled verification simulation, with a real sandbox activation-fee payment. */
    public UUID activate(UUID eventId, long organizer, boolean acceptedTerms, boolean simulateVerification) {
        if (!events.canManageTicketing(eventId, organizer)) throw new org.springframework.security.access.AccessDeniedException(
            "Only the organizer can activate paid sales"
        );
        var scope = events.salesScope(eventId, clock.instant());
        terms.requireActivationRequest(acceptedTerms, simulateVerification, scope.cancelled(), scope.eventStartsAt(), clock.instant());
        locks.lock("activation:" + eventId);
        var old = activations.find(eventId);
        if (old.isPresent()) {
            var previous = old.orElseThrow();
            if (previous.aggregate().permitsPaidSales()) return previous.paymentId();
            locks.lock("payment:" + previous.paymentId());
            var previousWork = require(previous.paymentId());
            if (previousWork.reusableActivation(clock.instant())) return previous.paymentId();
            // Keep the old invoice monitored. If it settles late it is automatically refunded.
            previousWork.replaceActivationAttempt();
            works.save(previousWork);
            works.wake(previousWork.id, clock.instant());
        }
        bank.validateAmount(terms.activationFee());
        PaymentWork w = terms.activation(eventId, organizer, clock.instant(), works.nextInvoice(), secrets.generate());
        works.insert(w);
        activations.save(
            new ActivationRepository.Activation(
                EventPaidSalesActivation.request(eventId, organizer, clock.instant()),
                w.id,
                true,
                "sandbox-v1"
            )
        );
        audit.record("payments", w.id, organizer, "ACTIVATION_REQUESTED_SIMULATED_VERIFICATION");
        return w.id;
    }

    @Override
    public PaymentView get(UUID id) {
        return view(require(id));
    }

    public PaymentWork authorized(UUID id, long user) {
        var w = require(id);
        if (
            w.payerId != user && !events.canManageTicketing(w.eventId, user)
        ) throw new org.springframework.security.access.AccessDeniedException("Payment is private");
        return w;
    }

    @Override
    public void abandon(UUID id) {
        locks.lock("payment:" + id);
        var w = require(id);
        w.abandon();
        works.save(w);
        works.wake(id, clock.instant());
        audit.record("payments", id, null, "PAYMENT_ABANDONED");
    }

    @Override
    public UUID refund(UUID id, String reason) {
        locks.lock("payment:" + id);
        var w = require(id);
        UUID refund = w.requestTicketRefund(reason, clock.instant());
        works.save(w);
        works.wake(id, clock.instant());
        audit.record("payments", id, null, "REFUND_REQUESTED");
        return refund;
    }

    public java.util.List<PaymentView> requiringReview() {
        return works.requiringReview().stream().map(this::get).toList();
    }

    public PaymentWork require(UUID id) {
        return works
            .find(id)
            .orElseThrow(() -> new com.biletflow.biletflow.common.application.EntityNotFoundException("Payment not found"));
    }

    private PaymentView view(PaymentWork w) {
        var r = w.isTicket() ? w.payment.getRefund().orElse(null) : null;
        return new PaymentView(
            w.id,
            w.orderId,
            w.status(),
            r == null ? null : r.getId().value(),
            r == null ? null : r.getStatus().name(),
            w.reviewRequired || "UNKNOWN".equals(w.operationState),
            w.operation,
            w.operationId,
            w.operationState,
            true
        );
    }
}
