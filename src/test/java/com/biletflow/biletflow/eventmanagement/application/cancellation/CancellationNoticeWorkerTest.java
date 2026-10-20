package com.biletflow.biletflow.eventmanagement.application.cancellation;

import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CancellationNoticeWorkerTest {

    private final Instant now = Instant.parse("2026-10-20T12:00:00Z");
    private final CancellationNoticeStore store = mock(CancellationNoticeStore.class);
    private final CancellationNoticeSender sender = mock(CancellationNoticeSender.class);
    private final CancellationNoticeStore.Claim claim = new CancellationNoticeStore.Claim(
        CancellationNotice.forRecipient(
            UUID.randomUUID(),
            "attendee@example.com",
            "Event",
            now.plusSeconds(3600),
            now.plusSeconds(7200),
            now
        ),
        UUID.randomUUID()
    );

    @Test
    void smtpFailureRetainsNoticeForRetry() {
        when(store.claim(now)).thenReturn(Optional.of(claim), Optional.empty());
        doThrow(new IllegalStateException("SMTP unavailable")).when(sender).send(claim.notice());
        new CancellationNoticeWorker(store, sender, Clock.fixed(now, ZoneOffset.UTC)).deliverPending();
        verify(store).retry(claim, now);
        verify(store, never()).complete(any(), any());
    }

    @Test
    void acknowledgesOnlyAfterSmtpAcceptedTheNotice() {
        when(store.claim(now)).thenReturn(Optional.of(claim), Optional.empty());
        new CancellationNoticeWorker(store, sender, Clock.fixed(now, ZoneOffset.UTC)).deliverPending();
        var order = inOrder(sender, store);
        order.verify(sender).send(claim.notice());
        order.verify(store).complete(claim, now);
        verify(store, never()).retry(any(), any());
    }
}
