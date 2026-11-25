package com.biletflow.biletflow.ordercheckout.ohs;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ordercheckout.application.CheckoutService;
import com.biletflow.biletflow.ordercheckout.application.view.CheckoutView;
import com.biletflow.biletflow.ordercheckout.ohs.dto.SupportOrderReference;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultSupportOrderOperations implements SupportOrderOperations {
    private final CheckoutService checkout;
    private final OrderCheckoutOhs references;
    private final EventManagementOhs events;
    private final CurrentActor actor;
    private final TransactionLocks locks;

    public DefaultSupportOrderOperations(CheckoutService checkout, OrderCheckoutOhs references,
                                         EventManagementOhs events, CurrentActor actor, TransactionLocks locks) {
        this.checkout = checkout;
        this.references = references;
        this.events = events;
        this.actor = actor;
        this.locks = locks;
    }

    @Override
    public Order inspect(UUID eventId, UUID orderId) {
        long user = actor.requireUserId();
        var reference = requireScope(eventId, orderId);
        if (!actor.isAdministrator() && !events.canHandleAttendeeSupport(eventId, user)) {
            throw new AuthorizationException("Cannot inspect this order");
        }
        return project(checkout.supportView(orderId), reference.ownerUserId());
    }

    @Override
    @Transactional
    public Order refund(UUID eventId, UUID orderId, boolean freeCancellation, String reason) {
        long user = actor.requireUserId();
        if (!actor.isAdministrator() && !events.canManageTicketing(eventId, user)) {
            throw new AuthorizationException("Only organizers or platform administrators can cancel or refund orders");
        }
        locks.lock("checkout:" + orderId);
        var reference = requireScope(eventId, orderId);
        var before = checkout.supportView(orderId);
        if (freeCancellation != (before.amountKzt().signum() == 0)) {
            throw new IllegalStateException("The requested action does not match the order amount");
        }
        if (freeCancellation && !"COMPLETED".equals(before.status())) {
            throw new IllegalStateException("Only completed free orders can be cancelled here");
        }
        if ("PENDING".equals(before.refundState()) || "REFUNDED".equals(before.refundState())) {
            throw new IllegalStateException("A refund is already pending or completed");
        }
        if (before.tickets().stream().anyMatch(t -> !"VALID".equals(t.status()))) {
            throw new IllegalStateException("Only unused tickets are eligible for cancellation or refund");
        }
        return project(checkout.refundForSupport(orderId, user, actor.isAdministrator(), reason), reference.ownerUserId());
    }

    private SupportOrderReference requireScope(UUID eventId, UUID orderId) {
        var reference = references.findSupportReference(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        if (!eventId.equals(reference.eventId())) throw new AuthorizationException("Order does not belong to this event");
        return reference;
    }

    private static Order project(CheckoutView view, Long ownerUserId) {
        return new Order(view.id(), view.eventId(), ownerUserId, view.status(), view.amountKzt(), view.paymentId(),
            view.refundState(), view.refundId(), view.createdAt(), view.tickets().stream()
                .map(t -> new Ticket(t.id(), t.ticketTypeId(), t.seatId(), t.attendeeEmail(), t.status())).toList());
    }
}

