package com.biletflow.biletflow.paymentsmonetization.persistence.settings.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pm_setting")
public class PaymentSettingJpaEntity {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "amount_kzt", precision = 19, scale = 2)
    private BigDecimal amountKzt;

    @Column(name = "rate", precision = 7, scale = 4)
    private BigDecimal rate;

    @Column(name = "updated_by", nullable = false)
    private long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PaymentSettingJpaEntity() {}

    public PaymentSettingJpaEntity(String key, BigDecimal amountKzt, long updatedBy, Instant updatedAt) {
        this.key = key;
        update(amountKzt, updatedBy, updatedAt);
    }

    public static PaymentSettingJpaEntity rate(String key, BigDecimal rate, long updatedBy, Instant updatedAt) {
        var row = new PaymentSettingJpaEntity();
        row.key = key;
        row.updateRate(rate, updatedBy, updatedAt);
        return row;
    }

    public void update(BigDecimal amountKzt, long updatedBy, Instant updatedAt) {
        this.amountKzt = amountKzt;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public void updateRate(BigDecimal rate, long updatedBy, Instant updatedAt) {
        this.rate = rate;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getAmountKzt() {
        return amountKzt;
    }

    public long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
