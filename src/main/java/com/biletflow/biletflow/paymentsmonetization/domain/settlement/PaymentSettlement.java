package com.biletflow.biletflow.paymentsmonetization.domain.settlement;

import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivation;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.*;
import java.time.Instant;
import java.util.*;

/** Pure financial policy. Callers persist the transition and its effects in one transaction. */
public final class PaymentSettlement {

    public record EventFacts(boolean registrationOpen, boolean cancelled, long organizerId, Instant startsAt) {}

    public record Context(EventFacts event, UUID activationPaymentId, EventPaidSalesActivation activation, boolean verificationSimulated) {
        boolean registrationOpen() {
            return event != null && event.registrationOpen();
        }

        boolean activationEligible(PaymentWork work, Instant now) {
            return event != null && !event.cancelled() && event.organizerId() == work.organizerId && event.startsAt().isAfter(now);
        }
    }

    public record Dispatch(String transactionId, String operation, UUID operationId) {}

    public record Outcome(String status, UUID refundId) {}

    public static final class Effects {

        public boolean charge;
        public UUID refundId;
        public boolean activationChanged;
        public Dispatch dispatch;
        public final List<Outcome> outcomes = new ArrayList<>();
        public final List<String> audit = new ArrayList<>();
    }

    private final Context context;
    private final Instant now;
    private final Effects effects = new Effects();

    private PaymentSettlement(Context context, Instant now) {
        this.context = context;
        this.now = now;
    }

    public static Effects settle(PaymentWork work, SettlementEvidence observation, Context context, Instant now) {
        var transition = new PaymentSettlement(context, now);
        transition.effects.dispatch = transition.apply(work, observation);
        return transition.effects;
    }

    public static Effects resolve(PaymentWork work, UUID expectedOperation, String resolution, Context context, Instant now) {
        var transition = new PaymentSettlement(context, now);
        transition.resolve(work, expectedOperation, resolution);
        return transition.effects;
    }

    private void resolve(PaymentWork w, UUID expectedOperation, String resolution) {
        if (!java.util.Objects.equals(expectedOperation, w.operationId)) throw new IllegalStateException(
            "Operation changed; reload before resolving"
        );
        if ("FULL_REFUND_CONFIRMED_COMPLETED".equals(resolution)) {
            if (w.bankTransactionId == null) throw new IllegalStateException("No verified provider transaction is known");
            if (w.isTicket()) {
                w.payment.reconcileCharge(w.bankTransactionId, now);
                effects.charge = true;
                if (
                    w.payment.getStatus() !=
                    com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.TicketOrderPaymentStatus.REFUNDED
                ) {
                    if (
                        w.payment.getRefund().isEmpty() || w.payment.getRefund().orElseThrow().getStatus() == RefundStatus.FAILED
                    ) w.payment.requestRefund("Full refund independently confirmed by bank support", now);
                    var refund = w.payment.getRefund().orElseThrow();
                    w.payment.completeRefund(w.bankTransactionId, now);
                    effects.refundId = refund.getId().value();
                    publish(w, "REFUNDED", refund.getId().value());
                }
            } else {
                w.activationPayment.reconcileCharge(w.bankTransactionId, now);
                w.activationPayment.requestCompensatingRefund();
                w.activationPayment.completeCompensatingRefund();
                var activation = context;
                if (activation.activationPaymentId().equals(w.id) && activation.activation().permitsPaidSales()) {
                    activation.activation().suspend("Activation fee refund confirmed by bank support", now);
                    effects.activationChanged = true;
                }
            }
            w.reviewRequired = false;
            w.operationState = "CONFIRMED";
        } else if ("CLEAR_REVIEW".equals(resolution)) {
            w.reviewRequired = false;
        } else {
            if (!w.uncertainMutation()) throw new IllegalStateException("No unresolved bank operation");
            if ("CONFIRMED_NOT_EXECUTED_RETRY".equals(resolution)) {
                w.operationAcknowledged = false;
                w.operationState = null;
                w.operation = null;
                w.operationAt = null;
                w.reviewRequired = false;
            } else if ("REFUND_CONFIRMED_REJECTED".equals(resolution) && w.isTicket() && "refund".equals(w.operation)) {
                if (w.payment.getRefund().orElseThrow().getStatus() != RefundStatus.REQUESTED) throw new IllegalStateException(
                    "Refund is no longer pending"
                );
                var refund = w.payment.getRefund().orElseThrow();
                w.payment.failRefund("BANK_SUPPORT_CONFIRMED_REJECTION", now);
                publish(w, "REFUND_FAILED", refund.getId().value());
                w.operationState = "REJECTED";
                w.reviewRequired = false;
            } else throw new IllegalArgumentException("Unsupported resolution");
        }
    }

    private Dispatch apply(PaymentWork w, SettlementEvidence o) {
        if (!w.invoiceId.equals(o.invoiceId())) {
            review(w, "INVOICE_MISMATCH");
            return null;
        }
        if (Set.of(SettlementStatus.NOT_FOUND, SettlementStatus.PENDING).contains(o.status())) return null;
        if (
            !w.id.toString().equals(o.paymentId()) ||
            (o.status() != SettlementStatus.REFUNDED && (o.amount() == null || w.amount.compareTo(o.amount()) != 0)) ||
            !"KZT".equals(o.currency()) ||
            !o.merchantVerified() ||
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
            return null;
        }
        // Explicit operator review is required before any further bank mutation.
        if (w.isTicket() && o.status() == SettlementStatus.AUTHORIZED && !w.charged() && !context.registrationOpen()) w.abandoned = true;
        boolean expired = !now.isBefore(w.deadline);
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
        if (w.uncertainMutation() && w.operationAt != null && now.isAfter(w.operationAt.plusSeconds(300))) {
            // Do not stop polling: a delayed REFUND/CHARGE may still resolve the uncertainty.
            if (!"UNKNOWN".equals(w.operationState)) effects.audit.add("BANK_MUTATION_STILL_UNRESOLVED");
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
                    var activation = context;
                    if (activation.activationPaymentId().equals(w.id) && activation.activation().permitsPaidSales()) {
                        activation.activation().suspend("Activation fee refunded", now);
                        effects.activationChanged = true;
                    }
                    w.operationState = "CONFIRMED";

                    effects.audit.add("ACTIVATION_FEE_REFUNDED");
                    return null;
                }
                // REFUND may be the first observation after downtime; post both ledger legs exactly once.
                if (!w.charged()) succeed(w, o.transactionId());
                if (w.payment.getStatus() != TicketOrderPaymentStatus.REFUNDED) {
                    if (
                        w.payment.getRefund().isEmpty() || w.payment.getRefund().orElseThrow().getStatus() == RefundStatus.FAILED
                    ) requestCompensation(w);
                    var r = w.payment.getRefund().orElseThrow();
                    w.payment.completeRefund(o.transactionId(), now);
                    effects.refundId = r.getId().value();
                    publish(w, "REFUNDED", r.getId().value());
                    effects.audit.add("REFUND_CONFIRMED");
                }
                w.operationState = "CONFIRMED";
            }
            case FAIL -> {
                if ("PENDING".equals(w.status())) {
                    if (w.isTicket()) w.payment.fail("BANK_DECLINED_OR_VOIDED", now);
                    else w.activationPayment.fail("BANK_DECLINED_OR_VOIDED", now);
                    if (w.isTicket()) publish(w, "FAILED", null);
                    effects.audit.add("PAYMENT_FAILED_OR_VOIDED");
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

        return null;
    }

    private Dispatch dispatch(PaymentWork w, String transaction, String operation) {
        if (w.uncertainMutation()) return null;
        w.operationAcknowledged = false;
        w.operation = operation;
        w.operationState = "SENT";
        w.operationAt = now;
        w.operationId =
            "refund".equals(operation) && w.isTicket() ? w.payment.getRefund().orElseThrow().getId().value() : UUID.randomUUID();

        effects.audit.add(operation.toUpperCase(java.util.Locale.ROOT) + "_DISPATCH_RECORDED");
        return new Dispatch(transaction, operation, w.operationId);
    }

    private void succeed(PaymentWork w, String reference) {
        boolean already = w.charged();
        if (w.isTicket()) w.payment.reconcileCharge(reference, now);
        else w.activationPayment.reconcileCharge(reference, now);
        if (already) return;
        if (w.isTicket()) {
            effects.charge = true;
            publish(w, "SUCCEEDED", null);
        } else {
            var activation = context;
            if (
                activation.activationPaymentId().equals(w.id) &&
                !w.abandoned &&
                now.isBefore(w.deadline) &&
                context.activationEligible(w, now)
            ) {
                activation.activation().activate(w.activationPayment, activation.verificationSimulated());
                effects.activationChanged = true;
            } else {
                w.activationPayment.requestCompensatingRefund();
                w.operationAcknowledged = false;
                w.operation = null;
                w.operationState = null;
                effects.audit.add("ACTIVATION_FEE_COMPENSATING_REFUND_REQUESTED");
            }
        }
        effects.audit.add("PAYMENT_CAPTURE_CONFIRMED_SANDBOX");
    }

    private void requestCompensation(PaymentWork w) {
        if (w.payment.getRefund().isEmpty() || w.payment.getRefund().orElseThrow().getStatus() == RefundStatus.FAILED) {
            w.payment.requestRefund("Checkout unavailable or provider-side refund", now);
            w.operationAcknowledged = false;
            w.operation = null;
            w.operationState = null;
            w.operationId = null;
            w.operationAt = null;
            effects.audit.add("COMPENSATING_REFUND_REQUESTED");
        }
    }

    private void publish(PaymentWork w, String outcome, UUID refund) {
        effects.outcomes.add(new Outcome(outcome, refund));
    }

    private void review(PaymentWork w, String reason) {
        w.reviewRequired = true;

        effects.audit.add(reason);
    }
}
