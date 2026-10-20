package com.biletflow.biletflow.ordercheckout.application;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ordercheckout.application.command.CreateCheckoutCommand;
import com.biletflow.biletflow.ordercheckout.application.view.CheckoutView;
import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import com.biletflow.biletflow.ordercheckout.domain.order.*;
import com.biletflow.biletflow.paymentsmonetization.ohs.*;
import com.biletflow.biletflow.paymentsmonetization.ohs.dto.StartPaymentCommand;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O&C process manager. Local OHS/database effects commit atomically; bank settlement is asynchronous. */
@Service
@Transactional(readOnly = true)
public class CheckoutService {

    private final CheckoutSessionRepository checkouts;
    private final OrderRepository orders;
    private final CheckoutProcessRepository processes;
    private final CheckoutInventoryOhs inventory;
    private final PaymentsOhs payments;
    private final EventManagementOhs events;
    private final TransactionLocks locks;
    private final FinancialAudit audit;
    private final CheckoutFingerprint fingerprints;
    private final Clock clock;

    public CheckoutService(
        CheckoutSessionRepository checkouts,
        OrderRepository orders,
        CheckoutProcessRepository processes,
        CheckoutInventoryOhs inventory,
        PaymentsOhs payments,
        EventManagementOhs events,
        TransactionLocks locks,
        FinancialAudit audit,
        CheckoutFingerprint fingerprints,
        Clock clock
    ) {
        this.checkouts = Objects.requireNonNull(checkouts, "checkouts cannot be null");
        this.orders = Objects.requireNonNull(orders, "orders cannot be null");
        this.processes = Objects.requireNonNull(processes, "processes cannot be null");
        this.inventory = Objects.requireNonNull(inventory, "inventory cannot be null");
        this.payments = Objects.requireNonNull(payments, "payments cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
        this.locks = Objects.requireNonNull(locks, "locks cannot be null");
        this.audit = Objects.requireNonNull(audit, "audit cannot be null");
        this.fingerprints = Objects.requireNonNull(fingerprints, "fingerprints cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Transactional
    public CheckoutView create(UUID id, long owner, CreateCheckoutCommand request) {
        if (request.selections().isEmpty() || request.selections().size() > 30) throw new IllegalArgumentException(
            "Supply 1 to 30 ticket selections"
        );
        locks.lock("event-lifecycle:" + request.eventId());
        locks.lock("checkout:" + id);
        String fingerprint = fingerprints.of(request);
        var prior = processes.find(id);
        if (prior.isPresent()) {
            var p = prior.orElseThrow();
            authorizeOwner(p, owner);
            if (!p.fingerprint().equals(fingerprint)) throw new IllegalStateException("Idempotency-Key was used for a different checkout");
            return view(p, requireCheckout(id));
        }
        var scope = events.salesScope(request.eventId(), clock.instant());
        if (!scope.registrationOpen()) throw new IllegalStateException("Event is not accepting registrations");
        var held = inventory.reserve(request.eventId(), id, request.selections());
        var checkout = new CheckoutSession(
            new CheckoutSessionId(id),
            owner,
            request.eventId(),
            held.assignedSeating() ? InventoryMode.RESERVED_SEATING : InventoryMode.GENERAL_ADMISSION
        );
        for (var line : held.lines())
            checkout.addItem(
                line.seatId() == null
                    ? new GeneralAdmissionCheckoutItem(line.ticketTypeId(), line.price())
                    : new AssignedSeatingCheckoutItem(line.ticketTypeId(), line.seatId(), line.price())
            );
        var process = new CheckoutProcess(
            id,
            owner,
            request.eventId(),
            fingerprint,
            held.expiresAt(),
            held.lines(),
            held.holds(),
            null,
            null,
            null
        );
        if (checkout.getFinalPrice().isZero()) {
            inventory.issue(process.eventId(), id, owner, process.lines(), process.holds());
            checkout.completeFreeCheckout();
            orders.save(Order.createFromCheckout(checkout));
            audit.record("ordercheckout", id, owner, "FREE_CHECKOUT_COMPLETED");
        } else {
            checkout.awaitPayment();
            var payment = payments.initiate(
                new StartPaymentCommand(id, process.eventId(), owner, checkout.getFinalPrice().amount(), process.deadline())
            );
            process = process.payment(payment.id());
            audit.record("ordercheckout", id, owner, "PAID_CHECKOUT_AWAITING_PAYMENT");
        }
        checkouts.save(checkout);
        processes.save(process);
        return view(process, checkout);
    }

    public CheckoutView get(UUID id, long actor) {
        var p = requireProcess(id);
        authorizeRead(p, actor);
        return view(p, requireCheckout(id));
    }

    public List<CheckoutView> mine(long actor) {
        return checkouts
            .findAllByOwnerUserId(actor)
            .stream()
            .map(c -> view(requireProcess(c.getId().value()), c))
            .toList();
    }

    @Transactional
    public CheckoutView cancel(UUID id, long actor) {
        locks.lock("checkout:" + id);
        var p = requireProcess(id);
        authorizeOwner(p, actor);
        var c = requireCheckout(id);
        if (
            c.getStatus() == CheckoutSessionStatus.CANCELLED ||
            c.getStatus() == CheckoutSessionStatus.EXPIRED ||
            c.getStatus() == CheckoutSessionStatus.PAYMENT_FAILED
        ) return view(p, c);
        if (c.getStatus() == CheckoutSessionStatus.COMPLETED) throw new IllegalStateException(
            "A completed order requires organizer cancellation or refund"
        );
        c.cancel();
        release(p);
        if (p.paymentId() != null) payments.abandon(p.paymentId());
        checkouts.save(c);
        audit.record("ordercheckout", id, actor, "CHECKOUT_CANCELLED");
        return view(p, c);
    }

    /** MVP policy: only organizers initiate full refunds of unused tickets; no partial refund or automatic customer entitlement. */
    @Transactional
    public CheckoutView refund(UUID id, long organizer, String reason) {
        locks.lock("checkout:" + id);
        var p = requireProcess(id);
        if (!events.canManageTicketing(p.eventId(), organizer)) throw new AuthorizationException(
            "Only the organizer can refund this order"
        );
        var c = requireCheckout(id);
        var existingOrder = orders.findById(new OrderId(id));
        if (existingOrder.isEmpty()) {
            if (p.paymentId() == null || !"SUCCEEDED".equals(payments.get(p.paymentId()).status())) throw new IllegalStateException(
                "No captured payment to refund"
            );
            UUID refund = payments.refund(p.paymentId(), reason);
            p = p.refund(CheckoutRefundStatus.PENDING, refund);
            processes.save(p);
            audit.record("ordercheckout", id, organizer, "UNFULFILLED_ORDER_REFUND_REQUESTED");
            return view(p, c);
        }
        var order = existingOrder.orElseThrow();
        if (p.refundState() == CheckoutRefundStatus.PENDING || p.refundState() == CheckoutRefundStatus.REFUNDED) return view(p, c);
        if (order.getFinalPrice().isZero()) {
            if (order.getStatus() != OrderStatus.CANCELLED) {
                inventory.cancelFreeOrder(p.eventId(), id);
                order.cancelOrderByManager();
                orders.save(order);
            }
            audit.record("ordercheckout", id, organizer, "FREE_ORDER_CANCELLED");
            return view(p, c);
        }
        inventory.prepareRefund(p.eventId(), id);
        UUID refund = payments.refund(p.paymentId(), reason);
        p = p.refund(CheckoutRefundStatus.PENDING, refund);
        processes.save(p);
        audit.record("ordercheckout", id, organizer, "ORDER_REFUND_REQUESTED");
        return view(p, c);
    }

    @Transactional
    public void onOutcome(PaymentOutcome outcome) {
        var initial = requireProcess(outcome.orderId());
        locks.lock("event-lifecycle:" + initial.eventId());
        locks.lock("checkout:" + outcome.orderId());
        var p = requireProcess(outcome.orderId());
        var c = requireCheckout(p.id());
        if (!Objects.equals(p.paymentId(), outcome.paymentId())) throw new IllegalStateException(
            "Payment outcome belongs to a different checkout"
        );
        switch (outcome.outcome()) {
            case "SUCCEEDED" -> {
                var financial = payments.get(outcome.paymentId());
                if ("REFUNDED".equals(financial.status())) {
                    onOutcome(new PaymentOutcome(outcome.paymentId(), outcome.orderId(), "REFUNDED", financial.refundId()));
                    return;
                }
                if (financial.reviewRequired()) throw new IllegalStateException("Payment settlement requires review before fulfillment");
                if ("REQUESTED".equals(financial.refundStatus()) && c.getStatus() == CheckoutSessionStatus.AWAITING_PAYMENT) {
                    c.cancel();
                    release(p);
                    checkouts.save(c);
                    processes.save(p.refund(CheckoutRefundStatus.PENDING, financial.refundId()));
                    return;
                }
                if (c.getStatus() == CheckoutSessionStatus.COMPLETED || p.refundState() == CheckoutRefundStatus.REFUNDED) return;
                if (
                    c.getStatus() != CheckoutSessionStatus.AWAITING_PAYMENT ||
                    !clock.instant().isBefore(p.deadline()) ||
                    !events.salesScope(p.eventId(), clock.instant()).registrationOpen()
                ) {
                    if (c.getStatus() == CheckoutSessionStatus.AWAITING_PAYMENT) {
                        c.expire();
                        checkouts.save(c);
                        release(p);
                    }
                    UUID refund = payments.refund(p.paymentId(), "Payment settled after checkout became unavailable");
                    processes.save(p.refund(CheckoutRefundStatus.PENDING, refund));
                    audit.record("ordercheckout", p.id(), null, "UNFULFILLED_PAYMENT_COMPENSATING");
                    return;
                }
                // Failures roll back event handling and are retried by the durable bus. They cannot lose a paid purchase.
                inventory.issue(p.eventId(), p.id(), p.ownerId(), p.lines(), p.holds());
                c.confirmPayment(outcome.paymentId());
                if (orders.findById(new OrderId(p.id())).isEmpty()) orders.save(Order.createFromCheckout(c));
                checkouts.save(c);
                audit.record("ordercheckout", p.id(), null, "PAID_ORDER_COMPLETED");
            }
            case "FAILED" -> {
                if (c.getStatus() == CheckoutSessionStatus.AWAITING_PAYMENT) {
                    c.markPaymentFailed();
                    checkouts.save(c);
                    release(p);
                    audit.record("ordercheckout", p.id(), null, "CHECKOUT_PAYMENT_FAILED");
                }
            }
            case "REFUNDED" -> {
                if (p.refundState() == CheckoutRefundStatus.REFUNDED) return;
                inventory.completeRefund(p.eventId(), p.id());
                var order = orders.findById(new OrderId(p.id()));
                if (order.isPresent()) {
                    var o = order.orElseThrow();
                    if (o.getStatus() == OrderStatus.COMPLETED) o.cancelOrderByManager();
                    o.markRefunded(outcome.refundId());
                    orders.save(o);
                } else if (c.getStatus() == CheckoutSessionStatus.AWAITING_PAYMENT) {
                    c.cancel();
                    release(p);
                    checkouts.save(c);
                }
                processes.save(p.refund(CheckoutRefundStatus.REFUNDED, outcome.refundId()));
                audit.record("ordercheckout", p.id(), null, "ORDER_REFUND_COMPLETED");
            }
            case "REFUND_FAILED" -> {
                if (p.refundState() != CheckoutRefundStatus.REFUNDED && Objects.equals(p.refundId(), outcome.refundId())) {
                    inventory.failRefund(p.eventId(), p.id());
                    processes.save(p.refund(CheckoutRefundStatus.FAILED, outcome.refundId()));
                    audit.record("ordercheckout", p.id(), null, "ORDER_REFUND_FAILED");
                }
            }
            default -> throw new IllegalArgumentException("Unknown payment outcome");
        }
    }

    @Transactional
    public void expire(UUID id) {
        locks.lock("checkout:" + id);
        var p = requireProcess(id);
        var c = requireCheckout(id);
        if (c.getStatus() != CheckoutSessionStatus.AWAITING_PAYMENT && c.getStatus() != CheckoutSessionStatus.IN_PROGRESS) return;
        var scope = events.salesScope(p.eventId(), clock.instant());
        if (clock.instant().isBefore(p.deadline()) && scope.registrationOpen()) return;
        if (scope.cancelled()) c.cancel();
        else c.expire();
        release(p);
        if (p.paymentId() != null) payments.abandon(p.paymentId());
        checkouts.save(c);
        audit.record("ordercheckout", id, null, "CHECKOUT_EXPIRED_OR_EVENT_CLOSED");
    }

    private void release(CheckoutProcess p) {
        inventory.release(p.eventId(), p.id(), p.holds());
    }

    private CheckoutProcess requireProcess(UUID id) {
        return processes.find(id).orElseThrow(() -> new EntityNotFoundException("Checkout not found"));
    }

    private CheckoutSession requireCheckout(UUID id) {
        return checkouts.findById(new CheckoutSessionId(id)).orElseThrow();
    }

    private void authorizeOwner(CheckoutProcess p, long user) {
        if (p.ownerId() != user) throw new AuthorizationException("Checkout is private");
    }

    private void authorizeRead(CheckoutProcess p, long user) {
        if (p.ownerId() != user && !events.canManageTicketing(p.eventId(), user)) throw new AuthorizationException("Checkout is private");
    }

    private CheckoutView view(CheckoutProcess p, CheckoutSession c) {
        String status = orders
            .findById(new OrderId(p.id()))
            .map(o -> o.getStatus().name())
            .orElse(c.getStatus().name());
        return new CheckoutView(
            p.id(),
            p.eventId(),
            status,
            c.getFinalPrice().amount(),
            p.deadline(),
            p.paymentId(),
            p.refundState() == null ? null : p.refundState().name(),
            p.refundId(),
            inventory.tickets(p.id()),
            true
        );
    }
}
