package com.biletflow.biletflow.payments.domain.revenue;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.payments.domain.exceptions.InsufficientBalanceException;
import com.biletflow.biletflow.payments.domain.exceptions.InvalidPayoutStateException;
import org.junit.jupiter.api.Test;

class OrganizerBalanceTest {

    private static final Long ORGANIZER_ID = 7L;

    @Test
    void aNewBalanceIsEmpty() {
        OrganizerBalance balance = OrganizerBalance.open(ORGANIZER_ID, Money.KZT);

        assertEquals(ORGANIZER_ID, balance.getOrganizerId());
        assertTrue(balance.getAvailable().isZero());
        assertTrue(balance.getPendingPayout().isZero());
        assertTrue(balance.getPaidOut().isZero());
        assertFalse(balance.hasDeficit());
    }

    @Test
    void creditIncreasesAvailable() {
        OrganizerBalance balance = OrganizerBalance.open(ORGANIZER_ID, Money.KZT);

        balance.credit(Money.kzt(10000));
        balance.credit(Money.kzt(5000));

        assertEquals(Money.kzt(15000), balance.getAvailable());
    }

    @Test
    void debitDecreasesAvailable() {
        OrganizerBalance balance = balanceWith(Money.kzt(10000));

        balance.debit(Money.kzt(4000));

        assertEquals(Money.kzt(6000), balance.getAvailable());
        assertFalse(balance.hasDeficit());
    }

    @Test
    void zeroAmountsAreRejected() {
        OrganizerBalance balance = balanceWith(Money.kzt(10000));

        assertThrows(InvalidPayoutStateException.class, () -> balance.credit(Money.zeroKzt()));
        assertThrows(InvalidPayoutStateException.class, () -> balance.debit(Money.zeroKzt()));
        assertThrows(InvalidPayoutStateException.class, () -> balance.reserveForPayout(Money.zeroKzt()));
    }

    @Test
    void reservingForPayoutMovesMoneyToPending() {
        OrganizerBalance balance = balanceWith(Money.kzt(10000));

        balance.reserveForPayout(Money.kzt(8000));

        assertEquals(Money.kzt(2000), balance.getAvailable());
        assertEquals(Money.kzt(8000), balance.getPendingPayout());
    }

    @Test
    void aPayoutCannotExceedTheAvailableBalance() {
        OrganizerBalance balance = balanceWith(Money.kzt(10000));

        assertThrows(InsufficientBalanceException.class, () -> balance.reserveForPayout(Money.kzt(10001)));
        assertEquals(Money.kzt(10000), balance.getAvailable());
        assertTrue(balance.getPendingPayout().isZero());
    }

    @Test
    void settlingAPayoutMovesPendingToPaidOut() {
        OrganizerBalance balance = balanceWith(Money.kzt(10000));
        balance.reserveForPayout(Money.kzt(8000));

        balance.settlePayout(Money.kzt(8000));

        assertTrue(balance.getPendingPayout().isZero());
        assertEquals(Money.kzt(8000), balance.getPaidOut());
        assertEquals(Money.kzt(2000), balance.getAvailable());
    }

    @Test
    void aFailedPayoutReturnsMoneyToAvailable() {
        OrganizerBalance balance = balanceWith(Money.kzt(10000));
        balance.reserveForPayout(Money.kzt(8000));

        balance.releasePayout(Money.kzt(8000));

        assertTrue(balance.getPendingPayout().isZero());
        assertEquals(Money.kzt(10000), balance.getAvailable());
        assertTrue(balance.getPaidOut().isZero());
    }

    @Test
    void settlingOrReleasingMoreThanPendingIsRejected() {
        OrganizerBalance balance = balanceWith(Money.kzt(10000));
        balance.reserveForPayout(Money.kzt(3000));

        assertThrows(InvalidPayoutStateException.class, () -> balance.settlePayout(Money.kzt(3001)));
        assertThrows(InvalidPayoutStateException.class, () -> balance.releasePayout(Money.kzt(3001)));
    }

    @Test
    void aRefundAfterPayoutCreatesADeficit() {
        OrganizerBalance balance = balanceWith(Money.kzt(10000));
        balance.reserveForPayout(Money.kzt(10000));
        balance.settlePayout(Money.kzt(10000));

        balance.debit(Money.kzt(4000));

        assertTrue(balance.getAvailable().isZero());
        assertEquals(Money.kzt(4000), balance.getDeficit());
        assertTrue(balance.hasDeficit());
    }

    @Test
    void noPayoutIsPossibleWhileADeficitExists() {
        OrganizerBalance balance = balanceWithDeficit(Money.kzt(4000));

        assertThrows(InsufficientBalanceException.class, () -> balance.reserveForPayout(Money.kzt(1)));
    }

    @Test
    void aSmallCreditOnlyReducesTheDeficit() {
        OrganizerBalance balance = balanceWithDeficit(Money.kzt(4000));

        balance.credit(Money.kzt(3000));

        assertEquals(Money.kzt(1000), balance.getDeficit());
        assertTrue(balance.getAvailable().isZero());
    }

    @Test
    void aLargeCreditPaysTheDeficitFirstThenIncreasesAvailable() {
        OrganizerBalance balance = balanceWithDeficit(Money.kzt(4000));

        balance.credit(Money.kzt(10000));

        assertFalse(balance.hasDeficit());
        assertEquals(Money.kzt(6000), balance.getAvailable());
    }

    @Test
    void aReleasedPayoutPaysADeficitCreatedInTheMeantime() {
        OrganizerBalance balance = balanceWith(Money.kzt(10000));
        balance.reserveForPayout(Money.kzt(10000));
        balance.debit(Money.kzt(3000));

        balance.releasePayout(Money.kzt(10000));

        assertFalse(balance.hasDeficit());
        assertEquals(Money.kzt(7000), balance.getAvailable());
    }

    private static OrganizerBalance balanceWith(Money available) {
        OrganizerBalance balance = OrganizerBalance.open(ORGANIZER_ID, Money.KZT);
        balance.credit(available);
        return balance;
    }

    private static OrganizerBalance balanceWithDeficit(Money deficit) {
        OrganizerBalance balance = OrganizerBalance.open(ORGANIZER_ID, Money.KZT);
        balance.debit(deficit);
        return balance;
    }
}
