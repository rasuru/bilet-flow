package com.biletflow.biletflow.paymentsmonetization.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import com.biletflow.biletflow.paymentsmonetization.application.view.PayoutView;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PayoutNoticeWorkerTest {

    private final Instant now = Instant.parse("2026-10-20T14:00:00Z");
    private final PayoutNoticeStore notices = mock(PayoutNoticeStore.class);
    private final PayoutNoticeSender sender = mock(PayoutNoticeSender.class);
    private final IamOhs users = mock(IamOhs.class);
    private final PayoutNoticeStore.Claim claim = new PayoutNoticeStore.Claim(
        UUID.randomUUID(),
        7,
        new PayoutView(UUID.randomUUID(), new BigDecimal("970.00"), "KZT", "COMPLETED", now, now, "SIM-test", null, true)
    );
    private final PayoutNoticeWorker worker = new PayoutNoticeWorker(notices, sender, users, Clock.fixed(now, ZoneOffset.UTC));

    PayoutNoticeWorkerTest() {
        when(notices.claim(now)).thenReturn(Optional.of(claim), Optional.empty());
    }

    @Test
    void sendsToCurrentVerifiedOrganizerEmailAndAcknowledgesOnlyAfterSend() {
        when(users.findVerifiedUserEmail(7L)).thenReturn(Optional.of(new VerifiedUserEmailView(7L, "organizer@example.com")));
        worker.deliverPending();
        var order = inOrder(sender, notices);
        order.verify(sender).send(claim.payout(), "organizer@example.com");
        order.verify(notices).complete(claim, now);
        verify(notices, never()).retry(any(), any());
    }

    @Test
    void missingVerifiedAddressDefersDelivery() {
        when(users.findVerifiedUserEmail(7L)).thenReturn(Optional.empty());
        worker.deliverPending();
        verifyNoInteractions(sender);
        verify(notices).retry(claim, now);
        verify(notices, never()).complete(any(), any());
    }

    @Test
    void smtpFailureRetriesWithoutChangingTheFinancialRecord() {
        when(users.findVerifiedUserEmail(7L)).thenReturn(Optional.of(new VerifiedUserEmailView(7L, "organizer@example.com")));
        doThrow(new IllegalStateException("SMTP unavailable")).when(sender).send(any(), any());
        worker.deliverPending();
        verify(notices).retry(claim, now);
        verify(notices, never()).complete(any(), any());
        assertEquals("COMPLETED", claim.payout().status());
    }
}
