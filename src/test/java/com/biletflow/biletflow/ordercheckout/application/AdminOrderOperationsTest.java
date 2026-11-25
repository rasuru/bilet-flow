package com.biletflow.biletflow.ordercheckout.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ordercheckout.domain.order.OrderStatus;
import com.biletflow.biletflow.ordercheckout.ohs.SupportOrderOperations;
import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentsOhs;
import com.biletflow.biletflow.paymentsmonetization.ohs.dto.PaymentView;
import com.biletflow.biletflow.ticketinventory.ohs.SupportTicketOperations;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminOrderOperationsTest {

    private final CurrentActor actor = mock(CurrentActor.class, CALLS_REAL_METHODS);
    private final OrderAdministrationQueryService issuedOrders = mock(OrderAdministrationQueryService.class);
    private final SupportOrderOperations orders = mock(SupportOrderOperations.class);
    private final EventManagementOhs events = mock(EventManagementOhs.class);
    private final PaymentsOhs payments = mock(PaymentsOhs.class);
    private final SupportTicketOperations tickets = mock(SupportTicketOperations.class);
    private final TransactionLocks locks = mock(TransactionLocks.class);
    private final FinancialAudit audit = mock(FinancialAudit.class);
    private final AdminOrderOperations operations = new AdminOrderOperations(
        actor,
        issuedOrders,
        orders,
        events,
        payments,
        tickets,
        locks,
        audit
    );

    private final UUID eventId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    @Test
    void rejectsNonAdministratorBeforeReadingOrLockingOrder() {
        when(actor.userId()).thenReturn(Optional.of(10L));

        assertThrows(AuthorizationException.class, () -> operations.get(orderId));
        assertThrows(AuthorizationException.class, () ->
            operations.execute(orderId, AdminOrderOperations.Action.RESEND_TICKET, ticketId, null)
        );
        verifyNoInteractions(issuedOrders, orders, events, payments, tickets, locks, audit);
    }

    @Test
    void requestsFullRefundOnlyAfterCheckingThePaymentAndAuditsTheReason() {
        givenOrder(BigDecimal.TEN, paymentId, "VALID");
        when(payments.get(paymentId)).thenReturn(payment(false));

        operations.execute(orderId, AdminOrderOperations.Action.REQUEST_REFUND, null, "Duplicate booking");

        var inOrder = inOrder(locks, payments, orders, audit);
        inOrder.verify(locks).lock("checkout:" + orderId);
        inOrder.verify(payments).requireReadyForAdministrativeRefund(paymentId);
        inOrder.verify(orders).refund(eventId, orderId, false, "Duplicate booking");
        inOrder.verify(audit).recordDetails("ordercheckout", orderId, 10L, "ADMIN_REQUEST_REFUND", "Duplicate booking");
    }

    @Test
    void refusesRefundForCheckedInTicketsEvenWithSucceededPayment() {
        givenOrder(BigDecimal.TEN, paymentId, "CHECKED_IN");
        when(payments.get(paymentId)).thenReturn(payment(false));

        assertThrows(IllegalStateException.class, () ->
            operations.execute(orderId, AdminOrderOperations.Action.REQUEST_REFUND, null, "Reason")
        );
        verify(orders, never()).refund(any(), any(), anyBoolean(), any());
    }

    @Test
    void cancelsAFreeOrderWithoutRequestingABankOperation() {
        givenOrder(BigDecimal.ZERO, null, "VALID");

        operations.execute(orderId, AdminOrderOperations.Action.CANCEL_FREE_ORDER, null, "Requested by customer");

        verify(orders).refund(eventId, orderId, true, "Requested by customer");
        verifyNoInteractions(payments);
    }

    private void givenOrder(BigDecimal amount, UUID payment, String ticketStatus) {
        when(actor.userId()).thenReturn(Optional.of(10L));
        when(actor.isAdministrator()).thenReturn(true);
        when(issuedOrders.search(new OrderAdminQueries.Filter(orderId, null, null, null, null, false), 0, 1)).thenReturn(
            List.of(
                new OrderAdminQueries.View(
                    orderId,
                    eventId,
                    11L,
                    OrderStatus.COMPLETED,
                    null,
                    BigDecimal.ZERO,
                    "KZT",
                    amount,
                    "KZT",
                    payment,
                    null
                )
            )
        );
        when(orders.inspect(eventId, orderId)).thenReturn(
            new SupportOrderOperations.Order(
                orderId,
                eventId,
                11L,
                "COMPLETED",
                amount,
                payment,
                null,
                null,
                Instant.EPOCH,
                List.of(new SupportOrderOperations.Ticket(ticketId, UUID.randomUUID(), null, "attendee@example.com", ticketStatus))
            )
        );
        when(events.supportEvent(eventId)).thenReturn(
            new EventManagementOhs.SupportEvent("Concert", Instant.EPOCH, Instant.EPOCH, null, null, false, false)
        );
    }

    private PaymentView payment(boolean review) {
        return new PaymentView(paymentId, orderId, "SUCCEEDED", null, null, review, null, null, "CONFIRMED", true);
    }
}
