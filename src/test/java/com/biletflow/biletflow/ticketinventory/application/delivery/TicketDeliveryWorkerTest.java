package com.biletflow.biletflow.ticketinventory.application.delivery;

import static org.mockito.Mockito.*;

import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.TicketEventDetails;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TicketDeliveryWorkerTest {

    private final TicketDeliveryStore store = mock(TicketDeliveryStore.class);
    private final TicketDocumentService documents = mock(TicketDocumentService.class);
    private final TicketDeliverySender sender = mock(TicketDeliverySender.class);
    private final Instant now = Instant.parse("2026-10-19T10:00:00Z");
    private final TicketDeliveryWorker worker = new TicketDeliveryWorker(store, documents, sender, Clock.fixed(now, ZoneOffset.UTC));
    private final TicketDeliveryStore.Claim claim = new TicketDeliveryStore.Claim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    private TicketDocument document(TicketStatus status, boolean cancelled) {
        return new TicketDocument(
            new TicketView(
                claim.ticketId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test@example.com",
                1L,
                null,
                UUID.randomUUID(),
                status,
                now
            ),
            "Ticket",
            new TicketEventDetails("Event", now, now.plusSeconds(3600), "Venue", "Address", "General admission", cancelled)
        );
    }

    private void pending(TicketDocument document) {
        when(store.claim(now)).thenReturn(Optional.of(claim), Optional.empty());
        when(documents.forDelivery(claim.ticketId())).thenReturn(document);
    }

    @Test
    void sendsAndAcknowledgesOnlyAfterTransportAcceptsMessage() {
        var document = document(TicketStatus.VALID, false);
        pending(document);
        worker.deliverPending();
        var ordered = inOrder(sender, store);
        ordered.verify(sender).send(document, claim.messageId());
        ordered.verify(store).complete(claim, false, now);
        verify(store, never()).retry(any(), any());
    }

    @Test
    void transportFailureLeavesWorkAvailableForDurableRetry() {
        var document = document(TicketStatus.VALID, false);
        pending(document);
        doThrow(new IllegalStateException("SMTP unavailable")).when(sender).send(document, claim.messageId());
        worker.deliverPending();
        verify(store).retry(claim, now);
        verify(store, never()).complete(any(), anyBoolean(), any());
    }

    @Test
    void pendingRefundDefersDeliveryUntilItIsResolved() {
        pending(document(TicketStatus.REFUND_PENDING, false));
        worker.deliverPending();
        verifyNoInteractions(sender);
        verify(store).retry(claim, now);
        verify(store, never()).complete(any(), anyBoolean(), any());
    }

    @Test
    void invalidTicketOrCancelledEventIsSkippedWithoutSending() {
        pending(document(TicketStatus.REFUNDED, false));
        worker.deliverPending();
        verifyNoInteractions(sender);
        verify(store).complete(claim, true, now);
        reset(store, documents);
        pending(document(TicketStatus.VALID, true));
        worker.deliverPending();
        verifyNoInteractions(sender);
        verify(store).complete(claim, true, now);
    }
}
