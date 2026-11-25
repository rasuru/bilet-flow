package com.biletflow.biletflow.support.application;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs.SupportEvent;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.ordercheckout.ohs.SupportOrderOperations;
import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentsOhs;
import com.biletflow.biletflow.paymentsmonetization.ohs.SupportActivationOperations;
import com.biletflow.biletflow.paymentsmonetization.ohs.dto.PaymentView;
import com.biletflow.biletflow.support.application.audit.SupportAuditRepository;
import com.biletflow.biletflow.support.application.audit.SupportAuditRepository.SupportAuditRecord;
import com.biletflow.biletflow.support.domain.supportcase.*;
import com.biletflow.biletflow.ticketinventory.ohs.SupportTicketOperations;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Case-scoped application composition. Operational state remains owned by the other BCs. */
@Service
@Transactional(readOnly = true)
public class SupportResolutionService {
    public enum Action { RESEND_TICKET, REVERSE_CHECK_IN, CANCEL_FREE_ORDER, REQUEST_REFUND }
    public record AvailableAction(Action action, UUID ticketId) {}
    public record Context(UUID caseId, Long requesterUserId, String requesterEmail, UUID eventId,
                          SupportEvent event, UUID linkedTicketId, SupportOrderOperations.Order order,
                          PaymentView payment, SupportActivationOperations.Activation activation,
                          List<AvailableAction> actions, List<SupportAuditRecord> history) {}

    private final SupportCaseRepository cases;
    private final CurrentActor actor;
    private final EventManagementOhs events;
    private final IamOhs iam;
    private final SupportOrderOperations orders;
    private final SupportTicketOperations tickets;
    private final PaymentsOhs payments;
    private final SupportActivationOperations activations;
    private final SupportAuditRepository audit;
    private final TransactionLocks locks;
    private final Clock clock;

    public SupportResolutionService(SupportCaseRepository cases, CurrentActor actor, EventManagementOhs events,
                                    IamOhs iam, SupportOrderOperations orders, SupportTicketOperations tickets,
                                    PaymentsOhs payments, SupportActivationOperations activations,
                                    SupportAuditRepository audit, TransactionLocks locks, Clock clock) {
        this.cases = cases;
        this.actor = actor;
        this.events = events;
        this.iam = iam;
        this.orders = orders;
        this.tickets = tickets;
        this.payments = payments;
        this.activations = activations;
        this.audit = audit;
        this.locks = locks;
        this.clock = clock;
    }

    public Context context(UUID caseId) {
        return build(requireAgentCase(caseId));
    }

    @Transactional
    public Context execute(UUID caseId, Action action, UUID ticketId, String reason) {
        if (action == null || (reason != null && reason.length() > 200)) {
            throw new IllegalArgumentException("An action and a reason of at most 200 characters are required");
        }
        locks.lock("support-case:" + caseId);
        var c = requireAgentCase(caseId);
        var before = build(c);
        if (!before.actions().contains(new AvailableAction(action, ticketId))) {
            throw new IllegalStateException("This action is not available for the linked records");
        }
        UUID orderId = c.getOrderId();
        switch (action) {
            case RESEND_TICKET -> tickets.resend(c.getEventId(), orderId, ticketId);
            case REVERSE_CHECK_IN -> tickets.reverseCheckIn(c.getEventId(), orderId, ticketId);
            case CANCEL_FREE_ORDER, REQUEST_REFUND -> {
                if (reason == null || reason.isBlank()) {
                    throw new IllegalArgumentException("A reason of up to 200 characters is required");
                }
                orders.refund(c.getEventId(), orderId, action == Action.CANCEL_FREE_ORDER, reason.trim());
            }
        }
        audit.append(new SupportAuditRecord(UUID.randomUUID(), caseId, c.getEventId(), clock.instant(),
            actor.requireUserId(), "ACTION_" + action.name(),
            (ticketId == null ? "orderId=" + orderId : "ticketId=" + ticketId)
                + (reason == null || reason.isBlank() ? "" : "; reason=" + reason.trim())));
        return build(c);
    }

    private SupportCase requireAgentCase(UUID id) {
        long user = actor.requireUserId();
        var c = cases.findById(new SupportCaseId(id)).orElseThrow(() -> new EntityNotFoundException("Support case not found"));
        if (c.isOwnedBy(user) || (!actor.isAdministrator() && (c.getType() != SupportCaseType.ATTENDEE_SUPPORT
                || c.getEventId() == null || !events.canHandleAttendeeSupport(c.getEventId(), user)))) {
            throw new AuthorizationException("Only authorized support agents can inspect or resolve case operations");
        }
        return c;
    }

    private Context build(SupportCase c) {
        var eventId = c.getEventId();
        var event = eventId == null ? null : events.supportEvent(eventId);
        var order = c.getOrderId() == null ? null : orders.inspect(eventId, c.getOrderId());
        boolean financial = eventId != null && (actor.isAdministrator() || events.canManageTicketing(eventId, actor.requireUserId()));
        var payment = financial && order != null && order.paymentId() != null ? payments.get(order.paymentId()) : null;
        var activation = actor.isAdministrator() && eventId != null && c.getType() == SupportCaseType.PLATFORM_SUPPORT
            ? activations.inspect(eventId).orElse(null) : null;
        var actions = new java.util.ArrayList<AvailableAction>();
        if (order != null && c.getStatus() != CaseStatus.RESOLVED) {
            boolean canReverse = actor.isAdministrator() || events.canCheckIn(eventId, actor.requireUserId());
            for (var ticket : order.tickets()) {
                if (c.getTicketId() != null && !c.getTicketId().equals(ticket.id())) continue;
                if ("VALID".equals(ticket.status()) && !event.cancelled() && !event.suspended()) {
                    actions.add(new AvailableAction(Action.RESEND_TICKET, ticket.id()));
                }
                if (canReverse && "CHECKED_IN".equals(ticket.status()) && !event.cancelled() && !event.suspended()) {
                    actions.add(new AvailableAction(Action.REVERSE_CHECK_IN, ticket.id()));
                }
            }
            boolean unused = order.tickets().stream().allMatch(t -> "VALID".equals(t.status()));
            boolean refundPending = "PENDING".equals(order.refundState()) || "REFUNDED".equals(order.refundState());
            if (financial && unused && !refundPending) {
                if (order.amountKzt().signum() == 0 && "COMPLETED".equals(order.status()) && !order.tickets().isEmpty()) {
                    actions.add(new AvailableAction(Action.CANCEL_FREE_ORDER, null));
                } else if (order.amountKzt().signum() > 0 && payment != null && "SUCCEEDED".equals(payment.status())
                        && !payment.reviewRequired() && ("COMPLETED".equals(order.status()) || order.tickets().isEmpty())) {
                    actions.add(new AvailableAction(Action.REQUEST_REFUND, null));
                }
            }
        }
        String email = iam.findVerifiedUserEmail(c.getRequesterUserId()).map(e -> e.email()).orElse(null);
        return new Context(c.getId().value(), c.getRequesterUserId(), email, eventId, event, c.getTicketId(), order,
            payment, activation, List.copyOf(actions), audit.findByCaseId(c.getId().value()));
    }
}

