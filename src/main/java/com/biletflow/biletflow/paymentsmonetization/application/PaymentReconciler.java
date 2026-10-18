package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.audit.FinancialAudit;
import com.biletflow.biletflow.common.events.EventBus;
import com.biletflow.biletflow.common.persistence.TransactionLocks;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.paymentsmonetization.config.HalykProperties;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.*;
import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentOutcome;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Network I/O is deliberately outside database transactions. Claims expire after worker crashes. */
@Component
public class PaymentReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentReconciler.class);
    private final PaymentWorkRepository works;
    private final PaymentGateway bank;
    private final ActivationRepository activations;
    private final PaymentLedger ledger;
    private final EventBus bus;
    private final FinancialAudit audit;
    private final TransactionLocks locks;
    private final EventManagementOhs events;
    private final HalykProperties config;
    private final Clock clock;
    private final TransactionTemplate tx;

    public PaymentReconciler(
        PaymentWorkRepository works,
        PaymentGateway bank,
        ActivationRepository activations,
        PaymentLedger ledger,
        EventBus bus,
        FinancialAudit audit,
        TransactionLocks locks,
        EventManagementOhs events,
        HalykProperties config,
        Clock clock,
        PlatformTransactionManager manager
    ) {
        this.works = works;
        this.bank = bank;
        this.activations = activations;
        this.ledger = ledger;
        this.bus = bus;
        this.audit = audit;
        this.locks = locks;
        this.events = events;
        this.config = config;
        this.clock = clock;
        this.tx = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${biletflow.payments.poll-delay-ms:5000}")
    public void poll() {
        for (UUID id : works.due(clock.instant())) reconcile(id);
    }

    public void reconcile(UUID id) {
        boolean leased = Boolean.TRUE.equals(tx.execute(s -> works.lease(id, clock.instant(), clock.instant().plusSeconds(90))));
        if (!leased) return;
        try {
            var snapshot = works.find(id).orElseThrow();
            var observation = bank.inspect(snapshot.invoiceId);
            Dispatch command = tx.execute(s -> apply(id, observation));
            if (command != null) {
                // SENT is already committed. A crash/timeout after this point NEVER causes automatic re-submission.
                try {
                    bank.mutate(command.transactionId(), command.operation(), command.operationId().toString());
                    tx.executeWithoutResult(status -> {
                        locks.lock("payment:" + id);
                        var latest = works.find(id).orElseThrow();
                        if (
                            command.operationId().equals(latest.operationId) &&
                            command.operation().equals(latest.operation) &&
                            latest.uncertainMutation()
                        ) {
                            latest.operationAcknowledged = true;
                            works.save(latest);
                        }
                    });
                } catch (Exception unknown) {
                    LOG.warn("Bank mutation outcome requires reconciliation for payment {}", id);
                }
            }
        } catch (Exception failure) {
            LOG.warn("Payment {} reconciliation deferred ({})", id, failure.getClass().getSimpleName());
        } finally {
            tx.executeWithoutResult(s -> {
                var w = works.find(id).orElseThrow();
                long delay = w.reviewRequired ? 300 : clock.instant().isAfter(w.deadline.plusSeconds(86400)) ? 3600 : 15;
                works.schedule(id, clock.instant().plusSeconds(delay));
            });
        }
    }

    private record Dispatch(String transactionId, String operation, UUID operationId) {}

    private Dispatch apply(UUID id, PaymentGateway.Observation o) {
        var initial = works.find(id).orElseThrow();
        if (!initial.isTicket()) locks.lock("activation:" + initial.eventId);
        locks.lock("payment:" + id);
        var w = works.find(id).orElseThrow();
        if (!w.invoiceId.equals(o.invoiceId())) {
            review(w, "INVOICE_MISMATCH");
            return null;
        }
        if (Set.of(SettlementStatus.NOT_FOUND, SettlementStatus.PENDING).contains(o.status())) return null;
        if (
            !w.id.toString().equals(o.paymentId()) ||
            (o.status() != SettlementStatus.REFUNDED && (o.amount() == null || w.amount.compareTo(o.amount()) != 0)) ||
            !"KZT".equals(o.currency()) ||
            !config.terminalId.equals(o.terminalId()) ||
            o.transactionId() == null ||
            o.transactionId().isBlank()
        ) {
            review(w, "PROVIDER_DATA_MISMATCH");
            return null;
        }
        String reference = w.isTicket() ? w.payment.getProviderReference() : w.activationPayment.getProviderReference();
        if (reference != null && !reference.equals(o.transactionId())) {
            review(w, "CONFLICTING_TRANSACTION");
            return null;
        }
        if ("REFUNDED".equals(w.status()) && o.status() != SettlementStatus.REFUNDED) {
            review(w, "SETTLEMENT_REGRESSED_AFTER_REFUND");
            return null;
        }
        if (w.bankTransactionId != null && !w.bankTransactionId.equals(o.transactionId())) {
            review(w, "CONFLICTING_PROVIDER_ATTEMPT");
            return null;
        }
        w.bankTransactionId = o.transactionId();
        if (w.reviewRequired) {
            works.save(w);
            return null;
        }
        // Explicit operator review is required before any further bank mutation.
        if (
            w.isTicket() &&
            o.status() == SettlementStatus.AUTHORIZED &&
            !w.charged() &&
            !events.salesScope(w.eventId, clock.instant()).registrationOpen()
        ) w.abandoned = true;
        boolean expired = !clock.instant().isBefore(w.deadline);
        boolean wantsRefund = w.isTicket()
            ? w.payment
                  .getRefund()
                  .map(r -> r.getStatus() == RefundStatus.REQUESTED)
                  .orElse(false)
            : "REFUND_REQUESTED".equals(w.status());
        var action = SettlementDecision.decide(o.status(), w.abandoned || expired, w.charged(), wantsRefund, w.uncertainMutation());
        // A previous capture/void operation is resolved when the bank reports CHARGE/CANCEL.
        if (o.status() == SettlementStatus.CAPTURED && !"refund".equals(w.operation)) {
            w.operationState = null;
            w.operationAcknowledged = false;
            w.operation = null;
        }
        if (w.uncertainMutation() && w.operationAt != null && clock.instant().isAfter(w.operationAt.plusSeconds(300))) {
            // Do not stop polling: a delayed REFUND/CHARGE may still resolve the uncertainty.
            if (!"UNKNOWN".equals(w.operationState)) audit.record("payments", id, null, "BANK_MUTATION_STILL_UNRESOLVED");
            w.operationState = "UNKNOWN";
        }
        switch (action) {
            case SUCCEED -> {
                succeed(w, o.transactionId());
                if (w.isTicket() && (w.abandoned || expired)) requestCompensation(w);
            }
            case CAPTURE -> {
                return dispatch(w, o.transactionId(), "charge");
            }
            case VOID -> {
                return dispatch(w, o.transactionId(), "cancel");
            }
            case REFUND -> {
                return dispatch(w, o.transactionId(), "refund");
            }
            case REFUNDED -> {
                if (!"REFUNDED".equals(w.status()) && (!"refund".equals(w.operation) || !w.operationAcknowledged)) {
                    // REFUND may include an external partial refund. Only our acknowledged full-refund request proves its scope.
                    review(w, "BANK_REFUND_REQUIRES_FULL_AMOUNT_CONFIRMATION");
                    return null;
                }
                if (!w.isTicket()) {
                    if ("REFUNDED".equals(w.status())) return null;
                    if (!w.charged()) {
                        w.abandoned = true;
                        succeed(w, o.transactionId());
                    }
                    w.activationPayment.requestCompensatingRefund();
                    w.activationPayment.completeCompensatingRefund();
                    locks.lock("activation:" + w.eventId);
                    var activation = activations.find(w.eventId).orElseThrow();
                    if (activation.paymentId().equals(w.id) && activation.aggregate().permitsPaidSales()) {
                        activation.aggregate().suspend("Activation fee refunded", clock.instant());
                        activations.save(activation);
                    }
                    w.operationState = "CONFIRMED";
                    works.save(w);
                    audit.record("payments", id, null, "ACTIVATION_FEE_REFUNDED");
                    return null;
                }
                // REFUND may be the first observation after downtime; post both ledger legs exactly once.
                if (!w.charged()) succeed(w, o.transactionId());
                if (w.payment.getStatus() != TicketOrderPaymentStatus.REFUNDED) {
                    if (
                        w.payment.getRefund().isEmpty() || w.payment.getRefund().orElseThrow().getStatus() == RefundStatus.FAILED
                    ) requestCompensation(w);
                    var r = w.payment.getRefund().orElseThrow();
                    w.payment.completeRefund(o.transactionId(), clock.instant());
                    ledger.refund(w, r.getId().value());
                    publish(w, "REFUNDED", r.getId().value());
                    audit.record("payments", id, null, "REFUND_CONFIRMED");
                }
                w.operationState = "CONFIRMED";
            }
            case FAIL -> {
                if ("PENDING".equals(w.status())) {
                    if (w.isTicket()) w.payment.fail("BANK_DECLINED_OR_VOIDED", clock.instant());
                    else w.activationPayment.fail("BANK_DECLINED_OR_VOIDED", clock.instant());
                    if (w.isTicket()) publish(w, "FAILED", null);
                    audit.record("payments", id, null, "PAYMENT_FAILED_OR_VOIDED");
                }
                w.operationState = "CONFIRMED";
            }
            case REVIEW -> {
                review(w, "UNSUPPORTED_OR_CONFLICTING_STATUS");
                return null;
            }
            case WAIT -> {
            }
        }
        works.save(w);
        return null;
    }

    private Dispatch dispatch(PaymentWork w, String transaction, String operation) {
        if (w.uncertainMutation()) return null;
        w.operationAcknowledged = false;
        w.operation = operation;
        w.operationState = "SENT";
        w.operationAt = clock.instant();
        w.operationId =
            "refund".equals(operation) && w.isTicket() ? w.payment.getRefund().orElseThrow().getId().value() : UUID.randomUUID();
        works.save(w);
        audit.record("payments", w.id, null, operation.toUpperCase(java.util.Locale.ROOT) + "_DISPATCH_RECORDED");
        return new Dispatch(transaction, operation, w.operationId);
    }

    private void succeed(PaymentWork w, String reference) {
        boolean already = w.charged();
        if (w.isTicket()) w.payment.reconcileCharge(reference, clock.instant());
        else w.activationPayment.reconcileCharge(reference, clock.instant());
        if (already) return;
        if (w.isTicket()) {
            ledger.charge(w);
            publish(w, "SUCCEEDED", null);
        } else {
            locks.lock("activation:" + w.eventId);
            var activation = activations.find(w.eventId).orElseThrow();
            var scope = events.salesScope(w.eventId, clock.instant());
            if (
                activation.paymentId().equals(w.id) &&
                !w.abandoned &&
                clock.instant().isBefore(w.deadline) &&
                !scope.cancelled() &&
                scope.organizerId() == w.organizerId &&
                scope.eventStartsAt().isAfter(clock.instant())
            ) {
                activation.aggregate().activate(w.activationPayment, activation.verificationSimulated());
                activations.save(activation);
            } else {
                w.activationPayment.requestCompensatingRefund();
                w.operationAcknowledged = false;
                w.operation = null;
                w.operationState = null;
                audit.record("payments", w.id, null, "ACTIVATION_FEE_COMPENSATING_REFUND_REQUESTED");
            }
        }
        audit.record("payments", w.id, null, "PAYMENT_CAPTURE_CONFIRMED_SANDBOX");
    }

    private void requestCompensation(PaymentWork w) {
        if (w.payment.getRefund().isEmpty() || w.payment.getRefund().orElseThrow().getStatus() == RefundStatus.FAILED) {
            w.payment.requestRefund("Checkout unavailable or provider-side refund", clock.instant());
            w.operationAcknowledged = false;
            w.operation = null;
            w.operationState = null;
            w.operationId = null;
            w.operationAt = null;
            audit.record("payments", w.id, null, "COMPENSATING_REFUND_REQUESTED");
        }
    }

    private void publish(PaymentWork w, String outcome, UUID refund) {
        bus.publish(new PaymentOutcome(w.id, w.orderId, outcome, refund).event());
    }

    private void review(PaymentWork w, String reason) {
        w.reviewRequired = true;
        works.save(w);
        audit.record("payments", w.id, null, reason);
    }
}
