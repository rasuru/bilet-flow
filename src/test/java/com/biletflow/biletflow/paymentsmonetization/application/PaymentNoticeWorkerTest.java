package com.biletflow.biletflow.paymentsmonetization.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.eventmanagement.ohs.dto.TicketEventDetailsView;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import com.biletflow.biletflow.paymentsmonetization.domain.notice.PaymentNoticeKind;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PaymentNoticeWorkerTest {

    private final Instant now = Instant.parse("2026-10-28T12:00:00Z");
    private final PaymentNoticeStore notices = mock(PaymentNoticeStore.class);
    private final PaymentNoticeSender sender = mock(PaymentNoticeSender.class);
    private final IamOhs users = mock(IamOhs.class);
    private final EventManagementOhs events = mock(EventManagementOhs.class);
    private final PaymentNoticeStore.Claim claim = new PaymentNoticeStore.Claim(
        UUID.randomUUID(),
        new PaymentNoticeStore.PaymentNotice(
            UUID.randomUUID(),
            PaymentNoticeKind.TICKET_PAYMENT_FAILED,
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("5000.00"),
            now
        )
    );
    private final PaymentNoticeWorker worker = new PaymentNoticeWorker(notices, sender, users, events, Clock.fixed(now, ZoneOffset.UTC));

    PaymentNoticeWorkerTest() {
        when(notices.claim(now)).thenReturn(Optional.of(claim), Optional.empty());
    }

    @Test
    void sendsToTheVerifiedRecipientWithTheEventTitleAndAcknowledgesAfterSending() {
        when(users.findVerifiedUserEmail(1L)).thenReturn(Optional.of(new VerifiedUserEmailView(1L, "buyer@example.com")));
        when(events.ticketDetails(claim.notice().eventId(), null)).thenReturn(
            new TicketEventDetailsView("Spring Concert", now, now, "Venue", "Address", "General admission", false)
        );

        worker.deliverPending();

        var order = inOrder(sender, notices);
        order.verify(sender).send(claim.notice(), "Spring Concert", "buyer@example.com");
        order.verify(notices).complete(claim, now);
        verify(notices, never()).retry(any(), any());
    }

    @Test
    void missingVerifiedAddressDefersDelivery() {
        when(users.findVerifiedUserEmail(1L)).thenReturn(Optional.empty());

        worker.deliverPending();

        verifyNoInteractions(sender);
        verify(notices).retry(claim, now);
    }

    @Test
    void anUndisplayableEventDoesNotBlockTheNotice() {
        when(users.findVerifiedUserEmail(1L)).thenReturn(Optional.of(new VerifiedUserEmailView(1L, "buyer@example.com")));
        when(events.ticketDetails(any(), any())).thenThrow(new IllegalStateException("Venue missing"));

        worker.deliverPending();

        verify(sender).send(claim.notice(), "your event", "buyer@example.com");
        verify(notices).complete(claim, now);
    }

    @Test
    void smtpFailureRetries() {
        when(users.findVerifiedUserEmail(1L)).thenReturn(Optional.of(new VerifiedUserEmailView(1L, "buyer@example.com")));
        doThrow(new IllegalStateException("SMTP unavailable")).when(sender).send(any(), any(), eq("buyer@example.com"));

        worker.deliverPending();

        verify(notices).retry(claim, now);
        verify(notices, never()).complete(any(), any());
    }
}
