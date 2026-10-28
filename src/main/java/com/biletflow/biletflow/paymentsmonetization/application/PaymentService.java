package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.CallbackSecrets;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.paymentsmonetization.application.command.ActivatePaidSalesCommand;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivation;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperation;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperationState;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentTerms;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import com.biletflow.biletflow.paymentsmonetization.ohs.*;
import com.biletflow.biletflow.paymentsmonetization.ohs.dto.PaymentView;
import com.biletflow.biletflow.paymentsmonetization.ohs.dto.StartPaymentCommand;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.ActivationNotAllowedException;
import com.biletflow.biletflow.ticketinventory.ohs.PaidSalesInventoryOhs;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
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
    private final PaidSalesInventoryOhs inventory;
    private final PaymentSettingsRepository settings;

    public PaymentService(
        PaymentWorkRepository works,
        ActivationRepository activations,
        EventManagementOhs events,
        TransactionLocks locks,
        FinancialAudit audit,
        Clock clock,
        PaymentTerms terms,
        PaymentGateway bank,
        CallbackSecrets secrets,
        PaidSalesInventoryOhs inventory,
        PaymentSettingsRepository settings
    ) {
        this.works = Objects.requireNonNull(works, "works cannot be null");
        this.activations = Objects.requireNonNull(activations, "activations cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
        this.locks = Objects.requireNonNull(locks, "locks cannot be null");
        this.audit = Objects.requireNonNull(audit, "audit cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.terms = Objects.requireNonNull(terms, "terms cannot be null");
        this.bank = Objects.requireNonNull(bank, "bank cannot be null");
        this.secrets = Objects.requireNonNull(secrets, "secrets cannot be null");
        this.inventory = Objects.requireNonNull(inventory, "inventory cannot be null");
        this.settings = Objects.requireNonNull(settings, "settings cannot be null");
    }

    @Override
    @Transactional
    public PaymentView initiate(StartPaymentCommand c) {
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
        audit.record("payments", w.getId(), c.payerId(), "PAYMENT_INITIATED_SANDBOX");
        return view(w);
    }

    /** Explicitly labelled verification simulation, with a real sandbox activation-fee payment. */
    @Transactional
    public UUID activate(ActivatePaidSalesCommand command) {
        UUID eventId = command.eventId();
        long organizer = command.organizerId();
        boolean acceptedTerms = command.acceptedTerms();
        boolean simulateVerification = command.simulatedVerification();
        if (!events.canManageTicketing(eventId, organizer)) throw new AuthorizationException("Only the organizer can activate paid sales");
        var scope = events.salesScope(eventId, clock.instant());
        terms.requireActivationRequest(acceptedTerms, simulateVerification, scope.cancelled(), scope.eventStartsAt(), clock.instant());
        locks.lock("activation:" + eventId);
        var old = activations.find(eventId);
        if (old.isPresent()) {
            var previous = old.orElseThrow();
            if (previous.aggregate().permitsPaidSales()) return previous.paymentId();
            locks.lock("payment:" + previous.paymentId());
            var previousWork = require(previous.paymentId());
            if (previousWork.getActivationPayment() != null) previous
                .aggregate()
                .requireNewFeeAttemptAllowed(previousWork.getActivationPayment());
            if (previousWork.reusableActivation(clock.instant())) return previous.paymentId();
            // Keep the old invoice monitored. If it settles late it is automatically refunded.
            previousWork.replaceActivationAttempt();
            works.save(previousWork);
            works.wake(previousWork.getId(), clock.instant());
        }
        // Checked only when a new fee would be charged; an existing activation or reusable attempt is returned as before.
        if (!inventory.hasPaidTicketType(eventId)) throw new ActivationNotAllowedException(
            "Add a paid ticket type before activating paid sales"
        );
        var currentTerms = activationTerms();
        bank.validateAmount(currentTerms.activationFee());
        PaymentWork w = currentTerms.activation(eventId, organizer, clock.instant(), works.nextInvoice(), secrets.generate());
        works.insert(w);
        activations.save(
            new ActivationRepository.Activation(
                EventPaidSalesActivation.request(eventId, organizer, clock.instant()),
                w.getId(),
                true,
                "sandbox-v1"
            )
        );
        audit.record("payments", w.getId(), organizer, "ACTIVATION_REQUESTED_SIMULATED_VERIFICATION");
        return w.getId();
    }

    @Override
    public PaymentView get(UUID id) {
        return view(require(id));
    }

    public PaymentWork authorized(UUID id, long user) {
        var w = require(id);
        if (w.getPayerId() != user && !events.canManageTicketing(w.getEventId(), user)) throw new AuthorizationException(
            "Payment is private"
        );
        return w;
    }

    public PaymentWork authorizedPayer(UUID id, long user) {
        var work = authorized(id, user);
        if (work.getPayerId() != user) throw new AuthorizationException("Only the payer can open this payment session");
        return work;
    }

    @Transactional
    @Override
    public void abandon(UUID id) {
        locks.lock("payment:" + id);
        var w = require(id);
        w.abandon();
        works.save(w);
        works.wake(id, clock.instant());
        audit.record("payments", id, null, "PAYMENT_ABANDONED");
    }

    @Transactional
    @Override
    public UUID refund(UUID id, String reason) {
        locks.lock("payment:" + id);
        var w = require(id);
        locks.lock("organizer-funds:" + w.getOrganizerId());
        UUID refund = w.requestTicketRefund(reason, clock.instant());
        works.save(w);
        works.wake(id, clock.instant());
        audit.record("payments", id, null, "REFUND_REQUESTED");
        return refund;
    }

    public List<PaymentView> requiringReview() {
        return works.requiringReview().stream().map(this::get).toList();
    }

    public PaymentWork require(UUID id) {
        return works.find(id).orElseThrow(() -> new EntityNotFoundException("Payment not found"));
    }

    public PaymentTerms activationTerms() {
        return settings
            .activationFee()
            .map(s -> terms.withActivationFee(s.amountKzt()))
            .orElse(terms);
    }

    private PaymentView view(PaymentWork w) {
        var r = w.isTicket() ? w.getPayment().getRefund().orElse(null) : null;
        return new PaymentView(
            w.getId(),
            w.getOrderId(),
            w.getStatus().name(),
            r == null ? null : r.getId().value(),
            r == null ? null : r.getStatus().name(),
            w.isReviewRequired() || w.getOperationState() == PaymentOperationState.UNKNOWN,
            operationView(w.getOperation()),
            w.getOperationId(),
            w.getOperationState() == null ? null : w.getOperationState().name(),
            true
        );
    }

    private String operationView(PaymentOperation operation) {
        if (operation == null) return null;
        return switch (operation) {
            case CAPTURE -> "charge";
            case VOID -> "cancel";
            case REFUND -> "refund";
        };
    }
}
