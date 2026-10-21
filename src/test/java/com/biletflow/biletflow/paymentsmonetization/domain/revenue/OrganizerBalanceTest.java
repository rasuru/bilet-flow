package com.biletflow.biletflow.paymentsmonetization.domain.revenue;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.*;
import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class OrganizerBalanceTest {

    private OrganizerBalance balance(String net, long pending, long paid, boolean blocked) {
        return OrganizerBalance.fromRevenue(7, new BigDecimal(net), Money.kzt(pending), Money.kzt(paid), blocked);
    }

    @Test
    void onlyRevenueNotAlreadyReservedOrPaidCanBeRequested() {
        var balance = balance("970", 200, 500, false);
        assertEquals(Money.kzt(270), balance.getAvailable());
        assertDoesNotThrow(() -> balance.requirePayoutAmount(Money.kzt(270)));
        assertThrows(InsufficientBalanceException.class, () -> balance.requirePayoutAmount(Money.kzt(271)));
    }

    @Test
    void aRefundAfterPayoutCreatesADeficitRatherThanNegativeMoney() {
        var balance = balance("-30", 0, 970, false);
        assertEquals(Money.zeroKzt(), balance.getAvailable());
        assertEquals(Money.kzt(1000), balance.getDebt());
        assertTrue(balance.hasDebt());
        assertThrows(InsufficientBalanceException.class, () -> balance.requirePayoutAmount(Money.kzt(1)));
    }

    @Test
    void laterRevenueCoversTheDeficitBeforeAnotherPayoutIsAllowed() {
        assertEquals(Money.kzt(470), balance("500", 0, 970, false).getDebt());
        var recovered = balance("1200", 0, 970, false);
        assertFalse(recovered.hasDebt());
        assertEquals(Money.kzt(230), recovered.getAvailable());
        assertDoesNotThrow(() -> recovered.requirePayoutAmount(Money.kzt(230)));
    }

    @Test
    void unresolvedFinancialStateBlocksEvenASufficientBalance() {
        var balance = balance("970", 0, 0, true);
        assertThrows(InvalidPayoutStateException.class, () -> balance.requirePayoutAmount(Money.kzt(1)));
        assertFalse(balance.hasDebt());
    }

    @Test
    void zeroAmountsAndForeignCurrenciesCannotBePaidOut() {
        var balance = balance("970", 0, 0, false);
        assertThrows(InvalidPayoutStateException.class, () -> balance.requirePayoutAmount(Money.zeroKzt()));
        assertThrows(IllegalArgumentException.class, () -> balance.requirePayoutAmount(Money.of(1, Currency.getInstance("USD"))));
    }
}
