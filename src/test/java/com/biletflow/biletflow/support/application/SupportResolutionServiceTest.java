package com.biletflow.biletflow.support.application;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import com.biletflow.biletflow.ordercheckout.ohs.SupportOrderOperations;
import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentsOhs;
import com.biletflow.biletflow.paymentsmonetization.ohs.SupportActivationOperations;
import com.biletflow.biletflow.paymentsmonetization.ohs.dto.PaymentView;
import com.biletflow.biletflow.support.application.SupportResolutionService.Action;
import com.biletflow.biletflow.support.application.SupportResolutionService.AvailableAction;
import com.biletflow.biletflow.support.application.audit.SupportAuditRepository;
import com.biletflow.biletflow.support.domain.supportcase.*;
import com.biletflow.biletflow.ticketinventory.ohs.SupportTicketOperations;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SupportResolutionServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final UUID EVENT = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private static final UUID OTHER_TICKET = UUID.randomUUID();
    private static final UUID TYPE = UUID.randomUUID();
    private static final UUID PAYMENT = UUID.randomUUID();
    private static final long REQUESTER = 1L;
    private static final long ORGANIZER = 2L;
    private static final long STAFF = 3L;
    private static final long ADMIN = 4L;
    private static final long OUTSIDER = 5L;

    @Test
    void organizerCanInspectAndResendTheLinkedTicketAndAuditTheRequest() {
        var f = new Fixture();
        var context = f.service.context(f.id());
        assertEquals("requester@example.com", context.requesterEmail());
        assertEquals("Festival", context.event().title());
        assertTrue(context.actions().contains(new AvailableAction(Action.RESEND_TICKET, TICKET)));
        var result = f.service.execute(f.id(), Action.RESEND_TICKET, TICKET, null);
        assertEquals(List.of(TICKET), f.resent);
        assertEquals("ACTION_RESEND_TICKET", result.history().getFirst().action());
        assertEquals(Long.valueOf(ORGANIZER), result.history().getFirst().actorUserId());
        assertEquals(f.id(), result.history().getFirst().caseId());
    }

    @Test
    void requesterAndOutsiderCannotAccessStaffContextOrPerformActions() {
        var f = new Fixture();
        for (long user : List.of(REQUESTER, OUTSIDER)) {
            f.user = user;
            assertThrows(AuthorizationException.class, () -> f.service.context(f.id()));
            assertThrows(AuthorizationException.class, () -> f.service.execute(f.id(), Action.RESEND_TICKET, TICKET, null));
        }
        assertTrue(f.resent.isEmpty());
        assertTrue(f.history.isEmpty());
    }

    @Test
    void eventStaffMayResendAndReverseCheckInButMayNotRefundOrInspectFinancialDetails() {
        var f = new Fixture();
        f.user = STAFF;
        assertEquals(null, f.service.context(f.id()).payment());
        assertFalse(f.service.context(f.id()).actions().contains(new AvailableAction(Action.REQUEST_REFUND, null)));
        assertThrows(IllegalStateException.class, () -> f.service.execute(f.id(), Action.REQUEST_REFUND, null, "Refund"));
        f.service.execute(f.id(), Action.RESEND_TICKET, TICKET, null);
        f.ticketStatus = "CHECKED_IN";
        f.service.execute(f.id(), Action.REVERSE_CHECK_IN, TICKET, "Accidental check-in");
        assertEquals(List.of(TICKET), f.reversed);
        assertEquals(0, f.refunds);
    }

    @Test
    void ticketSpecificCaseCannotOperateOnAnotherTicketEvenInTheSameOrder() {
        var f = new Fixture();
        assertThrows(IllegalStateException.class, () -> f.service.execute(f.id(), Action.RESEND_TICKET, OTHER_TICKET, null));
        assertThrows(IllegalStateException.class, () -> f.service.execute(f.id(), Action.RESEND_TICKET, UUID.randomUUID(), null));
        assertTrue(f.history.isEmpty());
        assertTrue(f.resent.isEmpty());
    }

    @Test
    void organizerCanRequestFullRefundOnlyWithReasonAndPendingStateDisablesAnotherRequest() {
        var f = new Fixture();
        assertTrue(f.service.context(f.id()).actions().contains(new AvailableAction(Action.REQUEST_REFUND, null)));
        assertThrows(IllegalArgumentException.class, () -> f.service.execute(f.id(), Action.REQUEST_REFUND, null, " "));
        assertEquals(0, f.refunds);
        var result = f.service.execute(f.id(), Action.REQUEST_REFUND, null, "  Customer request  ");
        assertEquals("PENDING", result.order().refundState());
        assertEquals("Customer request", f.lastReason);
        assertFalse(result.actions().contains(new AvailableAction(Action.REQUEST_REFUND, null)));
        assertThrows(IllegalStateException.class, () -> f.service.execute(f.id(), Action.REQUEST_REFUND, null, "Retry"));
        assertEquals(1, f.refunds);
        assertEquals(1, f.history.size());
    }

    @Test
    void freeCancellationUsesDistinctActionAndCheckedInOrdersCannotBeRefunded() {
        var f = new Fixture();
        f.ticketStatus = "CHECKED_IN";
        assertFalse(f.service.context(f.id()).actions().contains(new AvailableAction(Action.REQUEST_REFUND, null)));
        f.ticketStatus = "VALID";
        f.free = true;
        assertFalse(f.service.context(f.id()).actions().contains(new AvailableAction(Action.REQUEST_REFUND, null)));
        var result = f.service.execute(f.id(), Action.CANCEL_FREE_ORDER, null, "Customer requested cancellation");
        assertEquals("CANCELLED", result.order().status());
        assertTrue(result.actions().isEmpty());
        assertTrue(f.freeCancellation);
    }

    @Test
    void reviewRequiredPaymentsAndResolvedCasesDoNotOfferRefundActions() {
        var f = new Fixture();
        f.review = true;
        assertFalse(f.service.context(f.id()).actions().contains(new AvailableAction(Action.REQUEST_REFUND, null)));
        f.review = false;
        f.supportCase.changeStatus(CaseStatus.RESOLVED, NOW.plusSeconds(1));
        assertTrue(f.service.context(f.id()).actions().isEmpty());
        assertThrows(IllegalStateException.class, () -> f.service.execute(f.id(), Action.RESEND_TICKET, TICKET, null));
    }

    @Test
    void platformAdminCanRefundAndInspectLinkedActivation() {
        var f = new Fixture();
        f.user = ADMIN;
        assertTrue(f.service.context(f.id()).actions().contains(new AvailableAction(Action.REQUEST_REFUND, null)));
        f.service.execute(f.id(), Action.REQUEST_REFUND, null, "Approved by platform support");
        assertEquals(1, f.refunds);
        f.supportCase = SupportCase.open(SupportCaseType.PLATFORM_SUPPORT, ORGANIZER, "Activation issue", IssueCategory.PAYMENT,
            "Please inspect the activation", EVENT, null, null, NOW);
        var result = f.service.context(f.id());
        assertEquals("PENDING", result.activation().status());
        assertEquals(null, result.order());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void eventOnlyCaseDoesNotOfferOrderOrTicketActions() {
        var f = new Fixture();
        f.supportCase = SupportCase.open(SupportCaseType.ATTENDEE_SUPPORT, REQUESTER, "General question", IssueCategory.EVENT_INFORMATION,
            "Where is the venue?", EVENT, null, null, NOW);
        var context = f.service.context(f.id());
        assertEquals("Festival", context.event().title());
        assertEquals(null, context.order());
        assertEquals(null, context.payment());
        assertTrue(context.actions().isEmpty());
    }

    @Test
    void orderSpecificCaseCanResendAnyTicketInThatOrderButNotAnUnrelatedTicket() {
        var f = new Fixture();
        f.supportCase = SupportCase.open(SupportCaseType.ATTENDEE_SUPPORT, REQUESTER, "Order question", IssueCategory.TICKET_DELIVERY,
            "Please resend the tickets", EVENT, ORDER, null, NOW);
        assertTrue(f.service.context(f.id()).actions().contains(new AvailableAction(Action.RESEND_TICKET, OTHER_TICKET)));
        assertThrows(IllegalStateException.class, () -> f.service.execute(f.id(), Action.RESEND_TICKET, UUID.randomUUID(), null));
        f.service.execute(f.id(), Action.RESEND_TICKET, OTHER_TICKET, null);
        assertEquals(List.of(OTHER_TICKET), f.resent);
    }

    /** Fail-fast fakes for OHS boundaries; no Spring context or database is needed. */
    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
    }

    private static final class Fixture {
        long user = ORGANIZER;
        SupportCase supportCase = SupportCase.open(SupportCaseType.ATTENDEE_SUPPORT, REQUESTER, "Ticket problem",
            IssueCategory.TICKET_DELIVERY, "Please help", EVENT, ORDER, TICKET, NOW);
        boolean free;
        boolean review;
        String orderStatus = "COMPLETED";
        String ticketStatus = "VALID";
        String refundState;
        boolean freeCancellation;
        int refunds;
        String lastReason;
        final List<UUID> resent = new ArrayList<>();
        final List<UUID> reversed = new ArrayList<>();
        final List<SupportAuditRepository.SupportAuditRecord> history = new ArrayList<>();
        final SupportResolutionService service;

        Fixture() {
            CurrentActor actor = new CurrentActor() {
                @Override public Optional<Long> userId() { return Optional.of(user); }
                @Override public boolean isAdministrator() { return user == ADMIN; }
            };
            var cases = fake(SupportCaseRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findById" -> supportCase.getId().equals(args[0]) ? Optional.of(supportCase) : Optional.empty();
                default -> throw new AssertionError(method.getName());
            });
            var events = fake(EventManagementOhs.class, (proxy, method, args) -> switch (method.getName()) {
                case "canHandleAttendeeSupport", "canCheckIn" -> user == ORGANIZER || user == STAFF;
                case "canManageTicketing" -> user == ORGANIZER;
                case "supportEvent" -> new EventManagementOhs.SupportEvent("Festival", NOW, NOW.plusSeconds(3600),
                    "Hall", "Astana", false, false);
                default -> throw new AssertionError(method.getName());
            });
            var iam = fake(IamOhs.class, (proxy, method, args) -> {
                if (method.getName().equals("findVerifiedUserEmail")) return Optional.of(new VerifiedUserEmailView(REQUESTER, "requester@example.com"));
                throw new AssertionError(method.getName());
            });
            SupportOrderOperations orders = new SupportOrderOperations() {
                @Override public Order inspect(UUID eventId, UUID orderId) {
                    assertEquals(EVENT, eventId);
                    assertEquals(ORDER, orderId);
                    return order();
                }
                @Override public Order refund(UUID eventId, UUID orderId, boolean cancel, String reason) {
                    assertEquals(EVENT, eventId);
                    assertEquals(ORDER, orderId);
                    refunds++;
                    freeCancellation = cancel;
                    lastReason = reason;
                    if (cancel) { orderStatus = "CANCELLED"; ticketStatus = "CANCELLED"; }
                    else { refundState = "PENDING"; ticketStatus = "REFUND_PENDING"; }
                    return order();
                }
                private Order order() {
                    return new Order(ORDER, EVENT, REQUESTER, orderStatus, free ? BigDecimal.ZERO : BigDecimal.TEN,
                        free ? null : PAYMENT, refundState, null, NOW,
                        List.of(new Ticket(TICKET, TYPE, null, "requester@example.com", ticketStatus),
                            new Ticket(OTHER_TICKET, TYPE, null, "other@example.com", ticketStatus)));
                }
            };
            SupportTicketOperations tickets = new SupportTicketOperations() {
                @Override public void resend(UUID event, UUID order, UUID ticket) { resent.add(ticket); }
                @Override public void reverseCheckIn(UUID event, UUID order, UUID ticket) { reversed.add(ticket); ticketStatus = "VALID"; }
            };
            var payments = fake(PaymentsOhs.class, (proxy, method, args) -> {
                if (method.getName().equals("get")) return new PaymentView(PAYMENT, ORDER, "SUCCEEDED", null, null, review, null, null, null, true);
                throw new AssertionError(method.getName());
            });
            SupportActivationOperations activations = event -> Optional.of(new SupportActivationOperations.Activation("PENDING", null));
            SupportAuditRepository audit = new SupportAuditRepository() {
                @Override public void append(SupportAuditRecord record) { history.add(record); }
                @Override public List<SupportAuditRecord> findByCaseId(UUID caseId) {
                    return history.stream().filter(r -> caseId.equals(r.caseId())).toList();
                }
            };
            service = new SupportResolutionService(cases, actor, events, iam, orders, tickets, payments, activations,
                audit, key -> {}, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        UUID id() { return supportCase.getId().value(); }
    }
}

