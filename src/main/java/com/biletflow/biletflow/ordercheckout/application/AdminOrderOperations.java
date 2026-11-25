package com.biletflow.biletflow.ordercheckout.application;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ordercheckout.ohs.SupportOrderOperations;
import com.biletflow.biletflow.ordercheckout.ohs.SupportOrderOperations.Order;
import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentsOhs;
import com.biletflow.biletflow.paymentsmonetization.ohs.dto.PaymentView;
import com.biletflow.biletflow.ticketinventory.ohs.SupportTicketOperations;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminOrderOperations {

    public enum Action {
        RESEND_TICKET,
        CANCEL_FREE_ORDER,
        REQUEST_REFUND,
    }

    public record AvailableAction(Action action, UUID ticketId) {}

    public record Detail(
        OrderAdminQueries.View summary,
        Order order,
        String eventTitle,
        PaymentView payment,
        List<AvailableAction> actions
    ) {}

    private final CurrentActor actor;
    private final OrderAdministrationQueryService issuedOrders;
    private final SupportOrderOperations orders;
    private final EventManagementOhs events;
    private final PaymentsOhs payments;
    private final SupportTicketOperations tickets;
    private final TransactionLocks locks;
    private final FinancialAudit audit;

    public AdminOrderOperations(
        CurrentActor actor,
        OrderAdministrationQueryService issuedOrders,
        SupportOrderOperations orders,
        EventManagementOhs events,
        PaymentsOhs payments,
        SupportTicketOperations tickets,
        TransactionLocks locks,
        FinancialAudit audit
    ) {
        this.actor = actor;
        this.issuedOrders = issuedOrders;
        this.orders = orders;
        this.events = events;
        this.payments = payments;
        this.tickets = tickets;
        this.locks = locks;
        this.audit = audit;
    }

    public Detail get(UUID orderId) {
        actor.requireAdministrator();
        return detail(issuedOrder(orderId));
    }

    @Transactional
    public Detail execute(UUID orderId, Action action, UUID ticketId, String reason) {
        long admin = actor.requireAdministrator();
        locks.lock("checkout:" + orderId);
        var order = issuedOrder(orderId);
        var eventId = order.eventId();
        if (action == null || (reason != null && reason.length() > 200)) {
            throw new IllegalArgumentException("An action and a reason of at most 200 characters are required");
        }
        if (action == Action.REQUEST_REFUND && order.paymentId() != null) {
            payments.requireReadyForAdministrativeRefund(order.paymentId());
        }
        if (!detail(order).actions().contains(new AvailableAction(action, ticketId))) {
            throw new IllegalStateException("This action is no longer available for the order");
        }
        if (action == Action.RESEND_TICKET) {
            tickets.resend(eventId, orderId, ticketId);
            audit.recordDetails("ordercheckout", orderId, admin, "ADMIN_TICKET_EMAIL_REQUESTED", ticketId.toString());
        } else {
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required");
            orders.refund(eventId, orderId, action == Action.CANCEL_FREE_ORDER, reason.strip());
            audit.recordDetails("ordercheckout", orderId, admin, "ADMIN_" + action.name(), reason.strip());
        }
        return detail(issuedOrder(orderId));
    }

    private OrderAdminQueries.View issuedOrder(UUID orderId) {
        var filter = new OrderAdminQueries.Filter(orderId, null, null, null, null, false);
        return issuedOrders
            .search(filter, 0, 1)
            .stream()
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Issued order not found"));
    }

    private Detail detail(OrderAdminQueries.View initial) {
        // Re-read checkout state after obtaining any locks and after executing a command.
        var order = orders.inspect(initial.eventId(), initial.id());
        var event = events.supportEvent(order.eventId());
        var payment = order.paymentId() == null ? null : payments.get(order.paymentId());
        var actions = new ArrayList<AvailableAction>();

        if (!event.cancelled() && !event.suspended()) {
            order
                .tickets()
                .stream()
                .filter(ticket -> "VALID".equals(ticket.status()))
                .forEach(ticket -> actions.add(new AvailableAction(Action.RESEND_TICKET, ticket.id())));
        }

        boolean unused =
            !order.tickets().isEmpty() &&
            order
                .tickets()
                .stream()
                .allMatch(ticket -> "VALID".equals(ticket.status()));
        boolean refundPendingOrCompleted = "PENDING".equals(order.refundState()) || "REFUNDED".equals(order.refundState());
        if ("COMPLETED".equals(order.status()) && unused && !refundPendingOrCompleted) {
            if (order.amountKzt().signum() == 0) {
                actions.add(new AvailableAction(Action.CANCEL_FREE_ORDER, null));
            } else if (
                payment != null &&
                "SUCCEEDED".equals(payment.status()) &&
                !payment.reviewRequired() &&
                !"SENT".equals(payment.bankOperationState()) &&
                !"UNKNOWN".equals(payment.bankOperationState())
            ) {
                actions.add(new AvailableAction(Action.REQUEST_REFUND, null));
            }
        }
        return new Detail(initial, order, event.title(), payment, List.copyOf(actions));
    }
}
