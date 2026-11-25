package com.biletflow.biletflow.ordercheckout.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ordercheckout.application.command.CreateCheckoutCommand;
import com.biletflow.biletflow.ordercheckout.application.refund.RefundNoticeStore;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionRepository;
import com.biletflow.biletflow.ordercheckout.domain.order.OrderRepository;
import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentsOhs;
import com.biletflow.biletflow.promotions.ohs.PromotionsOhs;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CheckoutPrivateEventAccessTest {

    private static final long ORGANIZER = 10L;
    private static final long ATTENDEE = 20L;
    private static final String CODE = "invite-code-1234567890";
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private final UUID eventId = UUID.randomUUID();
    private final EventManagementOhs events = mock(EventManagementOhs.class);
    private final CheckoutInventoryOhs inventory = mock(CheckoutInventoryOhs.class);
    private final CheckoutProcessRepository processes = mock(CheckoutProcessRepository.class);
    private final CheckoutService service = new CheckoutService(
        mock(CheckoutSessionRepository.class),
        mock(OrderRepository.class),
        processes,
        inventory,
        mock(PaymentsOhs.class),
        events,
        mock(TransactionLocks.class),
        mock(FinancialAudit.class),
        mock(CheckoutFingerprint.class),
        Clock.fixed(NOW, ZoneOffset.UTC),
        mock(RefundNoticeStore.class),
        mock(PromotionsOhs.class)
    );
    private final CreateCheckoutCommand command = new CreateCheckoutCommand(
        eventId,
        List.of(new CheckoutInventoryOhs.Selection(UUID.randomUUID(), List.of(), List.of("guest@example.com")))
    );
    private final List<CheckoutInventoryOhs.QuoteSelection> quoteSelections = List.of(
        new CheckoutInventoryOhs.QuoteSelection(UUID.randomUUID(), 1)
    );

    private static final class ReachedInventory extends RuntimeException {}

    @BeforeEach
    void setUp() {
        when(processes.find(any())).thenReturn(Optional.empty());
        when(inventory.reserve(any(), any(), any())).thenThrow(new ReachedInventory());
        when(inventory.quote(any(), any())).thenThrow(new ReachedInventory());
        when(events.grantsAttendeeAccess(eventId, ATTENDEE, CODE)).thenReturn(true);
        when(events.grantsAttendeeAccess(eq(eventId), eq(ORGANIZER), any())).thenReturn(true);
    }

    private void privateEvent() {
        when(events.salesScope(eq(eventId), any())).thenReturn(
            new EventManagementOhs.SalesScope(eventId, ORGANIZER, true, false, NOW.plusSeconds(86_400), false)
        );
    }

    @Test
    void privateEventCheckoutWithoutTheCodeLooksLikeAMissingEvent() {
        privateEvent();
        assertThrows(EntityNotFoundException.class, () -> service.create(UUID.randomUUID(), ATTENDEE, command));
        assertThrows(EntityNotFoundException.class, () -> service.create(UUID.randomUUID(), ATTENDEE, command, "wrong-code-0000000000"));
        verify(inventory, never()).reserve(any(), any(), any());
    }

    @Test
    void privateEventCheckoutWithTheCodeReservesTickets() {
        privateEvent();
        assertThrows(ReachedInventory.class, () -> service.create(UUID.randomUUID(), ATTENDEE, command, CODE));
    }

    @Test
    void organizerNeedsNoCode() {
        privateEvent();
        assertThrows(ReachedInventory.class, () -> service.create(UUID.randomUUID(), ORGANIZER, command));
    }

    @Test
    void privateEventQuoteFollowsTheSameRule() {
        privateEvent();
        assertThrows(EntityNotFoundException.class, () -> service.quote(eventId, "PROMO10", quoteSelections, ATTENDEE, null));
        assertThrows(EntityNotFoundException.class, () -> service.quote(eventId, "PROMO10", quoteSelections, null, null));
        assertThrows(ReachedInventory.class, () -> service.quote(eventId, "PROMO10", quoteSelections, ATTENDEE, CODE));
    }

    @Test
    void publicEventsDoNotAskForAccess() {
        when(events.salesScope(eq(eventId), any())).thenReturn(
            new EventManagementOhs.SalesScope(eventId, ORGANIZER, true, false, NOW.plusSeconds(86_400))
        );
        assertThrows(ReachedInventory.class, () -> service.create(UUID.randomUUID(), ATTENDEE, command));
        verify(events, never()).grantsAttendeeAccess(any(), any(), any());
    }
}
