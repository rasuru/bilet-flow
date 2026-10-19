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
            return event != null && !event.cancelled() && event.organizerId() == work.getOrganizerId() && event.startsAt().isAfter(now);
        }
    }

    public record Dispatch(String transactionId, PaymentOperation operation, UUID operationId) {}

    public record Outcome(String status, UUID refundId) {}

    public static final class Effects {

        private boolean charge;

        public boolean isCharge() {
            return charge;
        }

        private UUID refundId;

        public UUID getRefundId() {
            return refundId;
        }

        private boolean activationChanged;

        public boolean isActivationChanged() {
            return activationChanged;
        }

        private Dispatch dispatch;

        public Dispatch getDispatch() {
            return dispatch;
        }

        private final List<Outcome> outcomes = new ArrayList<>();

        public List<Outcome> getOutcomes() {
            return List.copyOf(outcomes);
        }

        private final List<String> audit = new ArrayList<>();

        public List<String> getAudit() {
            return List.copyOf(audit);
        }
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

    public static Effects resolve(PaymentWork work, UUID expectedOperation, OperationResolution resolution, Context context, Instant now) {
        var transition = new PaymentSettlement(context, now);
        transition.resolve(work, expectedOperation, resolution);
        return transition.effects;
    }

    private void resolve(PaymentWork w, UUID expectedOperation, OperationResolution resolution) {
        if (!Objects.equals(expectedOperation, w.getOperationId())) throw new IllegalStateException(
            "Operation changed; reload before resolving"
        );
        if (resolution == OperationResolution.FULL_REFUND_CONFIRMED_COMPLETED) {
            if (w.getBankTransactionId() == null) throw new IllegalStateException("No verified provider transaction is known");
            if (w.isTicket()) {
                w.getPayment().reconcileCharge(w.getBankTransactionId(), now);
                effects.charge = true;
                if (w.getPayment().getStatus() != TicketOrderPaymentStatus.REFUNDED) {
                    if (
                        w.getPayment().getRefund().isEmpty() || w.getPayment().getRefund().orElseThrow().getStatus() == RefundStatus.FAILED
                    ) w.getPayment().requestRefund("Full refund independently confirmed by bank support", now);
                    var refund = w.getPayment().getRefund().orElseThrow();
                    w.getPayment().completeRefund(w.getBankTransactionId(), now);
                    effects.refundId = refund.getId().value();
                    publish(w, "REFUNDED", refund.getId().value());
                }
            } else {
                w.getActivationPayment().reconcileCharge(w.getBankTransactionId(), now);
                w.getActivationPayment().requestCompensatingRefund();
                w.getActivationPayment().completeCompensatingRefund();
                var activation = context;
                if (activation.activationPaymentId().equals(w.getId()) && activation.activation().permitsPaidSales()) {
                    activation.activation().suspend("Activation fee refund confirmed by bank support", now);
                    effects.activationChanged = true;
                }
            }
            w.clearReview();
            w.confirmOperation();
        } else if (resolution == OperationResolution.CLEAR_REVIEW) {
            w.clearReview();
        } else {
            if (!w.isMutationUncertain()) throw new IllegalStateException("No unresolved bank operation");
            if (resolution == OperationResolution.CONFIRMED_NOT_EXECUTED_RETRY) {
                w.allowRetryAfterConfirmedNonExecution();
            } else if (
                resolution == OperationResolution.REFUND_CONFIRMED_REJECTED && w.isTicket() && w.getOperation() == PaymentOperation.REFUND
            ) {
                if (w.getPayment().getRefund().orElseThrow().getStatus() != RefundStatus.REQUESTED) throw new IllegalStateException(
                    "Refund is no longer pending"
                );
                var refund = w.getPayment().getRefund().orElseThrow();
                w.getPayment().failRefund("BANK_SUPPORT_CONFIRMED_REJECTION", now);
                publish(w, "REFUND_FAILED", refund.getId().value());
                w.rejectOperation();
                w.clearReview();
            } else throw new IllegalArgumentException("Unsupported resolution");
        }
    }

    private Dispatch apply(PaymentWork w, SettlementEvidence o) {
        if (!w.getInvoiceId().equals(o.invoiceId())) {
            review(w, "INVOICE_MISMATCH");
            return null;
        }
        if (Set.of(SettlementStatus.NOT_FOUND, SettlementStatus.PENDING).contains(o.status())) return null;
        if (
            !w.getId().toString().equals(o.paymentId()) ||
            (o.status() != SettlementStatus.REFUNDED && (o.amount() == null || w.getAmount().compareTo(o.amount()) != 0)) ||
            !"KZT".equals(o.currency()) ||
            !o.merchantVerified() ||
            o.transactionId() == null ||
            o.transactionId().isBlank()
        ) {
            review(w, "PROVIDER_DATA_MISMATCH");
            return null;
        }
        String reference = w.isTicket() ? w.getPayment().getProviderReference() : w.getActivationPayment().getProviderReference();
        if (reference != null && !reference.equals(o.transactionId())) {
            review(w, "CONFLICTING_TRANSACTION");
            return null;
        }
        if (w.getStatus() == PaymentStatus.REFUNDED && o.status() != SettlementStatus.REFUNDED) {
            review(w, "SETTLEMENT_REGRESSED_AFTER_REFUND");
            return null;
        }
        if (w.getBankTransactionId() != null && !w.getBankTransactionId().equals(o.transactionId())) {
            review(w, "CONFLICTING_PROVIDER_ATTEMPT");
            return null;
        }
        w.recordVerifiedTransaction(o.transactionId());
        if (w.isReviewRequired()) {
            return null;
        }
        // Explicit operator review is required before any further bank mutation.
        if (w.isTicket() && o.status() == SettlementStatus.AUTHORIZED && !w.hasBeenCharged() && !context.registrationOpen()) w.abandon();
        boolean expired = !now.isBefore(w.getDeadline());
        boolean wantsRefund = w.isTicket()
            ? w
                  .getPayment()
                  .getRefund()
                  .map(r -> r.getStatus() == RefundStatus.REQUESTED)
                  .orElse(false)
            : w.getStatus() == PaymentStatus.REFUND_REQUESTED;
        var action = SettlementDecision.decide(
            o.status(),
            w.isAbandoned() || expired,
            w.hasBeenCharged(),
            wantsRefund,
            w.isMutationUncertain()
        );
        // A previous capture/void operation is resolved when the bank reports CHARGE/CANCEL.
        if (o.status() == SettlementStatus.CAPTURED && w.getOperation() != PaymentOperation.REFUND) {
            w.finishCaptureOperation();
        }
        if (w.isMutationUncertain() && w.getOperationAt() != null && now.isAfter(w.getOperationAt().plusSeconds(300))) {
            // Do not stop polling: a delayed REFUND/CHARGE may still resolve the uncertainty.
            if (w.getOperationState() != PaymentOperationState.UNKNOWN) effects.audit.add("BANK_MUTATION_STILL_UNRESOLVED");
            w.markOperationUnknown();
        }
        switch (action) {
            case SUCCEED -> {
                succeed(w, o.transactionId());
                if (w.isTicket() && (w.isAbandoned() || expired)) requestCompensation(w);
            }
            case CAPTURE -> {
                return dispatch(w, o.transactionId(), PaymentOperation.CAPTURE);
            }
            case VOID -> {
                return dispatch(w, o.transactionId(), PaymentOperation.VOID);
            }
            case REFUND -> {
                return dispatch(w, o.transactionId(), PaymentOperation.REFUND);
            }
            case REFUNDED -> {
                if (
                    w.getStatus() != PaymentStatus.REFUNDED && (w.getOperation() != PaymentOperation.REFUND || !w.isOperationAcknowledged())
                ) {
                    // REFUND may include an external partial refund. Only our acknowledged full-refund request proves its scope.
                    review(w, "BANK_REFUND_REQUIRES_FULL_AMOUNT_CONFIRMATION");
                    return null;
                }
                if (!w.isTicket()) {
                    if (w.getStatus() == PaymentStatus.REFUNDED) return null;
                    if (!w.hasBeenCharged()) {
                        w.abandon();
                        succeed(w, o.transactionId());
                    }
                    w.getActivationPayment().requestCompensatingRefund();
                    w.getActivationPayment().completeCompensatingRefund();
                    var activation = context;
                    if (activation.activationPaymentId().equals(w.getId()) && activation.activation().permitsPaidSales()) {
                        activation.activation().suspend("Activation fee refunded", now);
                        effects.activationChanged = true;
                    }
                    w.confirmOperation();

                    effects.audit.add("ACTIVATION_FEE_REFUNDED");
                    return null;
                }
                // REFUND may be the first observation after downtime; post both ledger legs exactly once.
                if (!w.hasBeenCharged()) succeed(w, o.transactionId());
                if (w.getPayment().getStatus() != TicketOrderPaymentStatus.REFUNDED) {
                    if (
                        w.getPayment().getRefund().isEmpty() || w.getPayment().getRefund().orElseThrow().getStatus() == RefundStatus.FAILED
                    ) requestCompensation(w);
                    var r = w.getPayment().getRefund().orElseThrow();
                    w.getPayment().completeRefund(o.transactionId(), now);
                    effects.refundId = r.getId().value();
                    publish(w, "REFUNDED", r.getId().value());
                    effects.audit.add("REFUND_CONFIRMED");
                }
                w.confirmOperation();
            }
            case FAIL -> {
                if (w.getStatus() == PaymentStatus.PENDING) {
                    if (w.isTicket()) w.getPayment().fail("BANK_DECLINED_OR_VOIDED", now);
                    else w.getActivationPayment().fail("BANK_DECLINED_OR_VOIDED", now);
                    if (w.isTicket()) publish(w, "FAILED", null);
                    effects.audit.add("PAYMENT_FAILED_OR_VOIDED");
                }
                w.confirmOperation();
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

    private Dispatch dispatch(PaymentWork w, String transaction, PaymentOperation operation) {
        if (w.isMutationUncertain()) return null;
        w.recordDispatch(operation, now);

        effects.audit.add(
            switch (operation) {
                case CAPTURE -> "CHARGE";
                case VOID -> "CANCEL";
                case REFUND -> "REFUND";
            } + "_DISPATCH_RECORDED"
        );
        return new Dispatch(transaction, operation, w.getOperationId());
    }

    private void succeed(PaymentWork w, String reference) {
        boolean already = w.hasBeenCharged();
        if (w.isTicket()) w.getPayment().reconcileCharge(reference, now);
        else w.getActivationPayment().reconcileCharge(reference, now);
        if (already) return;
        if (w.isTicket()) {
            effects.charge = true;
            publish(w, "SUCCEEDED", null);
        } else {
            var activation = context;
            if (
                activation.activationPaymentId().equals(w.getId()) &&
                !w.isAbandoned() &&
                now.isBefore(w.getDeadline()) &&
                context.activationEligible(w, now)
            ) {
                activation.activation().activate(w.getActivationPayment(), activation.verificationSimulated());
                effects.activationChanged = true;
            } else {
                w.getActivationPayment().requestCompensatingRefund();
                w.prepareActivationCompensation();
                effects.audit.add("ACTIVATION_FEE_COMPENSATING_REFUND_REQUESTED");
            }
        }
        effects.audit.add("PAYMENT_CAPTURE_CONFIRMED_SANDBOX");
    }

    private void requestCompensation(PaymentWork w) {
        if (w.getPayment().getRefund().isEmpty() || w.getPayment().getRefund().orElseThrow().getStatus() == RefundStatus.FAILED) {
            w.getPayment().requestRefund("Checkout unavailable or provider-side refund", now);
            w.resetOperation();
            effects.audit.add("COMPENSATING_REFUND_REQUESTED");
        }
    }

    private void publish(PaymentWork w, String outcome, UUID refund) {
        effects.outcomes.add(new Outcome(outcome, refund));
    }

    private void review(PaymentWork w, String reason) {
        w.requireReview();

        effects.audit.add(reason);
    }
}
