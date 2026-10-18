package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.audit.FinancialAudit;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.common.events.EventBus;
import com.biletflow.biletflow.common.persistence.TransactionLocks;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.paymentsmonetization.config.HalykProperties;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivation;
import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.ActivationFeePayment;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.*;
import com.biletflow.biletflow.paymentsmonetization.ohs.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
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
    private final HalykProperties config;

    public PaymentService(
        PaymentWorkRepository works,
        ActivationRepository activations,
        EventManagementOhs events,
        TransactionLocks locks,
        FinancialAudit audit,
        Clock clock,
        HalykProperties config
    ) {
        this.works = works;
        this.activations = activations;
        this.events = events;
        this.locks = locks;
        this.audit = audit;
        this.clock = clock;
        this.config = config;
    }

    @Override
    public PaymentView initiate(Start c) {
        Objects.requireNonNull(c);
        Objects.requireNonNull(c.orderId());
        Objects.requireNonNull(c.eventId());
        if (
            c.payerId() <= 0 || c.amount() == null || c.amount().compareTo(BigDecimal.TEN) < 0 || c.amount().scale() > 2
        ) throw new IllegalArgumentException("Halyk sandbox payments must be at least 10 KZT with at most two decimals");
        if (c.expiresAt() == null || !c.expiresAt().isAfter(clock.instant())) throw new IllegalStateException("Checkout expired");
        locks.lock("payment-order:" + c.orderId());
        var existing = works.findOrder(c.orderId());
        if (existing.isPresent()) {
            var w = existing.orElseThrow();
            if (
                !w.eventId.equals(c.eventId()) || w.payerId != c.payerId() || w.amount.compareTo(c.amount()) != 0
            ) throw new IllegalStateException("Payment idempotency conflict");
            return view(w);
        }
        var scope = events.salesScope(c.eventId(), clock.instant());
        if (!scope.registrationOpen()) throw new IllegalStateException("Event is not accepting purchases");
        var activation = activations.find(c.eventId()).orElseThrow(() -> new IllegalStateException("Paid sales are not activated"));
        if (!activation.aggregate().permitsPaidSales()) throw new IllegalStateException("Paid sales are not active");
        PaymentWork w = base("TICKET", c.eventId(), c.payerId(), scope.organizerId(), c.amount(), c.expiresAt());
        w.orderId = c.orderId();
        w.payment = TicketOrderPayment.initiate(c.orderId(), c.eventId(), scope.organizerId(), Money.kzt(c.amount()));
        w.id = w.payment.getId().value();
        works.insert(w);
        audit.record("payments", w.id, c.payerId(), "PAYMENT_INITIATED_SANDBOX");
        return view(w);
    }

    /** Explicitly labelled verification simulation, with a real sandbox activation-fee payment. */
    public UUID activate(UUID eventId, long organizer, boolean acceptedTerms, boolean simulateVerification) {
        if (!acceptedTerms || !simulateVerification) throw new IllegalArgumentException(
            "Accept demonstration paid-event terms and simulated verification"
        );
        if (!events.canManageTicketing(eventId, organizer)) throw new org.springframework.security.access.AccessDeniedException(
            "Only the organizer can activate paid sales"
        );
        var scope = events.salesScope(eventId, clock.instant());
        if (scope.cancelled() || !scope.eventStartsAt().isAfter(clock.instant())) throw new IllegalStateException(
            "Event cannot activate paid sales"
        );
        locks.lock("activation:" + eventId);
        var old = activations.find(eventId);
        if (old.isPresent()) {
            var previous = old.orElseThrow();
            if (previous.aggregate().permitsPaidSales()) return previous.paymentId();
            locks.lock("payment:" + previous.paymentId());
            var previousWork = require(previous.paymentId());
            if (
                "PENDING".equals(previousWork.status()) && clock.instant().isBefore(previousWork.deadline) && !previousWork.abandoned
            ) return previous.paymentId();
            if (previousWork.charged() && !"REFUNDED".equals(previousWork.status())) throw new IllegalStateException(
                "Previous activation payment requires reconciliation"
            );
            // Keep the old invoice monitored. If it settles late it is automatically refunded.
            previousWork.abandoned = true;
            works.save(previousWork);
            works.wake(previousWork.id, clock.instant());
        }
        PaymentWork w = base("ACTIVATION", eventId, organizer, organizer, config.activationFee, clock.instant().plusSeconds(1800));
        w.fee = BigDecimal.ZERO;
        w.activationPayment = ActivationFeePayment.initiate(eventId, organizer, Money.kzt(config.activationFee));
        w.id = w.activationPayment.getId().value();
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

    private PaymentWork base(String purpose, UUID event, long payer, long organizer, BigDecimal amount, Instant deadline) {
        PaymentWork w = new PaymentWork();
        w.purpose = purpose;
        w.eventId = event;
        w.payerId = payer;
        w.organizerId = organizer;
        w.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        w.fee = w.amount.multiply(config.processingFeeRate).setScale(2, RoundingMode.HALF_UP);
        w.invoiceId = works.nextInvoice();
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        w.callbackSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        w.deadline = deadline;
        return w;
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
        w.abandoned = true;
        works.save(w);
        works.wake(id, clock.instant());
        audit.record("payments", id, null, "PAYMENT_ABANDONED");
    }

    @Override
    public UUID refund(UUID id, String reason) {
        locks.lock("payment:" + id);
        var w = require(id);
        if (!w.isTicket()) throw new IllegalStateException("Activation fee refund policy is not part of ticket refunds");
        var current = w.payment.getRefund();
        if (current.isPresent() && current.orElseThrow().getStatus() != RefundStatus.FAILED) return current.orElseThrow().getId().value();
        if (reason == null || reason.isBlank() || reason.length() > 200) throw new IllegalArgumentException(
            "Refund reason is required, max 200 characters"
        );
        var refund = w.payment.requestRefund(reason, clock.instant());
        w.operationAcknowledged = false;
        w.operation = null;
        w.operationId = null;
        w.operationState = null;
        w.operationAt = null;
        works.save(w);
        works.wake(id, clock.instant());
        audit.record("payments", id, null, "REFUND_REQUESTED");
        return refund.getId().value();
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
