package com.biletflow.biletflow.promotions.domain.campaign;

import com.biletflow.biletflow.common.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Discount(DiscountType type, BigDecimal value) {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public Discount {
        Objects.requireNonNull(type, "Discount type cannot be null");
        Objects.requireNonNull(value, "Discount value cannot be null");

        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than 0");
        }

        if (type == DiscountType.PERCENTAGE && value.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100");
        }

        value = value.stripTrailingZeros();
        if (value.scale() < 0) {
            value = value.setScale(0);
        }
    }

    public static Discount percentage(BigDecimal percent) {
        return new Discount(DiscountType.PERCENTAGE, percent);
    }

    public static Discount percentage(int percent) {
        return percentage(BigDecimal.valueOf(percent));
    }

    public static Discount fixedKzt(Money amount) {
        Objects.requireNonNull(amount, "Discount amount cannot be null");
        if (!Money.KZT.equals(amount.currency())) {
            throw new IllegalArgumentException("Fixed discount must be in KZT");
        }
        return new Discount(DiscountType.FIXED_KZT, amount.amount());
    }

    public Money calculateFor(Money base) {
        Objects.requireNonNull(base, "Base amount cannot be null");

        return switch (type) {
            case PERCENTAGE -> Money.of(
                base.amount().multiply(value).divide(ONE_HUNDRED, base.currency().getDefaultFractionDigits(), RoundingMode.HALF_UP),
                base.currency()
            );
            case FIXED_KZT -> {
                Money fixed = Money.of(value, base.currency());
                yield fixed.isGreaterThan(base) ? base : fixed;
            }
        };
    }
}
