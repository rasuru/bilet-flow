package com.biletflow.biletflow.paymentsmonetization.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.*;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.application.command.RequestPayoutCommand;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.*;
import com.biletflow.biletflow.paymentsmonetization.domain.payout.*;
import com.biletflow.biletflow.paymentsmonetization.domain.revenue.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PayoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T14:00:00Z");
    private final Map<PayoutId, Payout> rows = new HashMap<>();
    private final PayoutRepository payouts = mock(PayoutRepository.class);
    private final OrganizerBalanceRepository balances = mock(OrganizerBalanceRepository.class);
    private final FinancialAudit audit = mock(FinancialAudit.class);
    private BigDecimal revenue = new BigDecimal("970.00");
    private boolean blocked;
    private final PayoutService service;

    PayoutServiceTest() {
        when(payouts.findById(any())).thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))));
        when(payouts.save(any())).thenAnswer(call -> {
            Payout p = call.getArgument(0);
            rows.put(p.getId(), p);
            return p;
        });
        when(balances.get(anyLong())).thenAnswer(call -> {
            BigDecimal pending = rows
                .values()
                .stream()
                .filter(p -> p.getStatus() == PayoutStatus.REQUESTED)
                .map(p -> p.getAmount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = rows
                .values()
                .stream()
                .filter(p -> p.getStatus() == PayoutStatus.COMPLETED)
                .map(p -> p.getAmount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return OrganizerBalance.fromRevenue(call.getArgument(0), revenue, Money.kzt(pending), Money.kzt(paid), blocked);
        });
        service = new PayoutService(payouts, balances, mock(TransactionLocks.class), audit, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RequestPayoutCommand request(UUID id, String amount) {
        return new RequestPayoutCommand(7, id, new BigDecimal(amount), true);
    }

    @Test
    void requestReservesNetRevenueAndIdenticalReplayKeepsOnePayout() {
        UUID id = UUID.randomUUID();
        assertEquals("REQUESTED", service.request(request(id, "970")).status());
        assertEquals(new BigDecimal("0.00"), service.balance(7).availableKzt());
        assertEquals(new BigDecimal("970.00"), service.balance(7).pendingPayoutKzt());
        assertEquals(id, service.request(request(id, "970.00")).id());
        verify(payouts, times(1)).save(any());
        verify(audit, times(1)).record("payments", id, 7L, "PAYOUT_REQUESTED_SIMULATED");
        assertThrows(InsufficientBalanceException.class, () -> service.request(request(UUID.randomUUID(), "0.01")));
    }

    @Test
    void replayWithDifferentAmountAndAnotherOrganizerAreRejected() {
        UUID id = UUID.randomUUID();
        service.request(request(id, "500"));
        assertThrows(InvalidPayoutStateException.class, () -> service.request(request(id, "501")));
        assertThrows(AuthorizationException.class, () -> service.request(new RequestPayoutCommand(8, id, new BigDecimal("500"), true)));
        assertThrows(AuthorizationException.class, () -> service.get(id, 8));
        assertThrows(EntityNotFoundException.class, () -> service.get(UUID.randomUUID(), 7));
    }

    @Test
    void successfulSimulationCompletesOnceAndPreservesIdentityOnReplay() {
        UUID id = UUID.randomUUID();
        service.request(request(id, "500"));
        service.process(new PayoutId(id), true);
        service.process(new PayoutId(id), true);
        var view = service.get(id, 7);
        assertEquals("COMPLETED", view.status());
        assertTrue(view.simulated());
        assertEquals("SIM-" + id, view.providerReference());
        assertEquals(new BigDecimal("500.00"), service.balance(7).paidOutKzt());
        assertEquals(new BigDecimal("470.00"), service.balance(7).availableKzt());
        assertEquals(view, service.request(request(id, "500")));
        verify(audit, times(1)).record("payments", id, null, "PAYOUT_COMPLETED_SIMULATED");
    }

    @Test
    void failedSimulationReleasesFundsAndRetryRequiresANewId() {
        UUID id = UUID.randomUUID();
        service.request(request(id, "970"));
        service.process(new PayoutId(id), false);
        assertEquals("FAILED", service.request(request(id, "970")).status());
        assertEquals(new BigDecimal("970.00"), service.balance(7).availableKzt());
        assertEquals("REQUESTED", service.request(request(UUID.randomUUID(), "970")).status());
    }

    @Test
    void pendingRefundBlocksNewPayoutAndFailsAnAlreadyReservedSimulation() {
        UUID id = UUID.randomUUID();
        service.request(request(id, "500"));
        blocked = true;
        assertTrue(service.balance(7).reconciliationPending());
        assertEquals(new BigDecimal("0.00"), service.balance(7).availableKzt());
        assertThrows(InvalidPayoutStateException.class, () -> service.request(request(UUID.randomUUID(), "1")));
        service.process(new PayoutId(id), true);
        assertEquals("FAILED", service.get(id, 7).status());
        blocked = false;
        assertEquals(new BigDecimal("970.00"), service.balance(7).availableKzt());
    }

    @Test
    void refundBeforeCompletionPreventsTransferAndRefundAfterCompletionCreatesDeficit() {
        UUID first = UUID.randomUUID();
        service.request(request(first, "970"));
        revenue = new BigDecimal("-30.00");
        service.process(new PayoutId(first), true);
        assertEquals("FAILED", service.get(first, 7).status());
        assertEquals(new BigDecimal("30.00"), service.balance(7).deficitKzt());
        revenue = new BigDecimal("970.00");
        UUID second = UUID.randomUUID();
        service.request(request(second, "970"));
        service.process(new PayoutId(second), true);
        revenue = new BigDecimal("-30.00");
        assertEquals(new BigDecimal("1000.00"), service.balance(7).deficitKzt());
        assertThrows(InsufficientBalanceException.class, () -> service.request(request(UUID.randomUUID(), "1")));
        revenue = new BigDecimal("1940.00");
        assertEquals(new BigDecimal("970.00"), service.balance(7).availableKzt());
    }

    @Test
    void invalidAmountsConsentAndPaginationAreRejectedBeforeReservation() {
        for (String amount : List.of("0", "-1", "0.001", "100000000000000000"))
            assertThrows(IllegalArgumentException.class, () -> request(UUID.randomUUID(), amount));
        assertThrows(IllegalArgumentException.class, () -> new RequestPayoutCommand(7, UUID.randomUUID(), BigDecimal.ONE, false));
        assertThrows(IllegalArgumentException.class, () -> service.list(7, -1, 50));
        assertThrows(IllegalArgumentException.class, () -> service.list(7, 0, 101));
        verify(payouts, never()).save(any());
    }
}
