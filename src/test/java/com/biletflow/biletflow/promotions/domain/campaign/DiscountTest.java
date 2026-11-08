package com.biletflow.biletflow.promotions.domain.campaign;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class DiscountTest {
    @Test
    void percentageDiscountIsCalculatedFromTheBaseAmount() {
        assertEquals(Money.kzt(2_500), Discount.percentage(25).calculateFor(Money.kzt(10_000)));
    }

    @Test
    void percentageDiscountIsRoundedToTheCurrencyScale() {
        assertEquals(Money.kzt(new BigDecimal("3.33")), Discount.percentage(new BigDecimal("33.3")).calculateFor(Money.kzt(10)));
    }

    @Test
    void hundredPercentDiscountMakesTheTicketsFree() {
        assertEquals(Money.kzt(5_000), Discount.percentage(100).calculateFor(Money.kzt(5_000)));
    }

    @Test
    void fixedDiscountIsCappedAtTheBaseAmount() {
        Discount discount = Discount.fixedKzt(Money.kzt(3_000));

        assertEquals(Money.kzt(3_000), discount.calculateFor(Money.kzt(10_000)));
        assertEquals(Money.kzt(2_000), discount.calculateFor(Money.kzt(2_000)));
    }

    @Test
    void invalidDiscountValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Discount.percentage(0));
        assertThrows(IllegalArgumentException.class, () -> Discount.percentage(101));
        assertThrows(IllegalArgumentException.class, () -> Discount.fixedKzt(Money.zeroKzt()));
        Money dollars = Money.of(BigDecimal.TEN, Currency.getInstance("USD"));
        assertThrows(IllegalArgumentException.class, () -> Discount.fixedKzt(dollars));
    }
}
