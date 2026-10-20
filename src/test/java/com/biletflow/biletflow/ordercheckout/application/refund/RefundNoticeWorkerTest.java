package com.biletflow.biletflow.ordercheckout.application.refund;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RefundNoticeWorkerTest {

    private final Instant now = Instant.parse("2026-10-20T12:00:00Z");
    private final RefundNotice notice = new RefundNotice(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        42,
        "Event",
        new BigDecimal("1000.00"),
        now
    );
    private final RefundNoticeStore store = mock(RefundNoticeStore.class);
    private final RefundNoticeSender sender = mock(RefundNoticeSender.class);
    private final IamOhs users = mock(IamOhs.class);
    private final RefundNoticeStore.Claim claim = new RefundNoticeStore.Claim(notice, UUID.randomUUID());
    private final RefundNoticeWorker worker = new RefundNoticeWorker(store, sender, users, Clock.fixed(now, ZoneOffset.UTC));

    private void ready() {
        when(store.claim(now)).thenReturn(Optional.of(claim), Optional.empty());
        when(users.findVerifiedUserEmail(42L)).thenReturn(Optional.of(new VerifiedUserEmailView(42L, "buyer@example.com")));
    }

    @Test
    void acknowledgesOnlyAfterSmtpAcceptanceAndResolvesThePurchaserThroughIam() {
        ready();
        worker.deliverPending();
        var order = inOrder(sender, store);
        order.verify(sender).send(notice, "buyer@example.com");
        order.verify(store).complete(claim, now);
        verify(store, never()).retry(any(), any());
    }

    @Test
    void smtpFailureKeepsReceiptForRetryWithoutAcknowledgement() {
        ready();
        doThrow(new IllegalStateException("SMTP unavailable")).when(sender).send(any(), anyString());
        worker.deliverPending();
        verify(store).retry(claim, now);
        verify(store, never()).complete(any(), any());
    }

    @Test
    void missingVerifiedEmailDefersWithoutUsingAnAttendeeAddressOrLosingReceipt() {
        ready();
        when(users.findVerifiedUserEmail(42L)).thenReturn(Optional.empty());
        worker.deliverPending();
        verifyNoInteractions(sender);
        verify(store).retry(claim, now);
        verify(store, never()).complete(any(), any());
    }
}
