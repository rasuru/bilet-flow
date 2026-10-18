package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.audit.FinancialAudit;
import com.biletflow.biletflow.common.events.EventBus;
import com.biletflow.biletflow.common.persistence.TransactionLocks;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.RefundStatus;
import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentOutcome;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentAccessService {

    private final PaymentWorkRepository works;
    private final TransactionLocks locks;
    private final FinancialAudit audit;
    private final EventBus bus;
    private final Clock clock;
    private final PaymentLedger ledger;
    private final ActivationRepository activations;

    public PaymentAccessService(
        PaymentWorkRepository works,
        TransactionLocks locks,
        FinancialAudit audit,
        EventBus bus,
        Clock clock,
        PaymentLedger ledger,
        ActivationRepository activations
    ) {
        this.works = works;
        this.locks = locks;
        this.audit = audit;
        this.bus = bus;
        this.clock = clock;
        this.ledger = ledger;
        this.activations = activations;
    }

    public void callback(String invoice, String secret) {
        if (invoice == null || !invoice.matches("[0-9]{6,15}") || secret == null || secret.length() > 200) throw denied();
        var w = works.findInvoice(invoice).orElseThrow(PaymentAccessService::denied);
        if (
            !MessageDigest.isEqual(w.callbackSecret.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))
        ) throw denied();
        // Only a hint: a forged/repeated callback can never mark payment as paid.
        works.wake(w.id, clock.instant());
    }

    public void resolve(UUID id, long actor, UUID expectedOperation, String resolution, String evidence) {
        if (evidence == null || evidence.isBlank() || evidence.length() > 200) throw new IllegalArgumentException(
            "Bank support reference is required (max 200 characters)"
        );
        var initial = works.find(id).orElseThrow();
        if (!initial.isTicket()) locks.lock("activation:" + initial.eventId);
        locks.lock("payment:" + id);
        var w = works.find(id).orElseThrow();
        if (!java.util.Objects.equals(expectedOperation, w.operationId)) throw new IllegalStateException(
            "Operation changed; reload before resolving"
        );
        if ("FULL_REFUND_CONFIRMED_COMPLETED".equals(resolution)) {
            if (w.bankTransactionId == null) throw new IllegalStateException("No verified provider transaction is known");
            if (w.isTicket()) {
                w.payment.reconcileCharge(w.bankTransactionId, clock.instant());
                ledger.charge(w);
                if (
                    w.payment.getStatus() !=
                    com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.TicketOrderPaymentStatus.REFUNDED
                ) {
                    if (
                        w.payment.getRefund().isEmpty() || w.payment.getRefund().orElseThrow().getStatus() == RefundStatus.FAILED
                    ) w.payment.requestRefund("Full refund independently confirmed by bank support", clock.instant());
                    var refund = w.payment.getRefund().orElseThrow();
                    w.payment.completeRefund(w.bankTransactionId, clock.instant());
                    ledger.refund(w, refund.getId().value());
                    bus.publish(new PaymentOutcome(w.id, w.orderId, "REFUNDED", refund.getId().value()).event());
                }
            } else {
                w.activationPayment.reconcileCharge(w.bankTransactionId, clock.instant());
                w.activationPayment.requestCompensatingRefund();
                w.activationPayment.completeCompensatingRefund();
                var activation = activations.find(w.eventId).orElseThrow();
                if (activation.paymentId().equals(w.id) && activation.aggregate().permitsPaidSales()) {
                    activation.aggregate().suspend("Activation fee refund confirmed by bank support", clock.instant());
                    activations.save(activation);
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
                w.payment.failRefund("BANK_SUPPORT_CONFIRMED_REJECTION", clock.instant());
                bus.publish(new PaymentOutcome(w.id, w.orderId, "REFUND_FAILED", refund.getId().value()).event());
                w.operationState = "REJECTED";
                w.reviewRequired = false;
            } else throw new IllegalArgumentException("Unsupported resolution");
        }
        works.save(w);
        works.wake(id, clock.instant());
        audit.recordDetails("payments", id, actor, "OPERATOR_" + resolution, evidence);
    }

    private static org.springframework.security.access.AccessDeniedException denied() {
        return new org.springframework.security.access.AccessDeniedException("Invalid callback authentication");
    }
}
