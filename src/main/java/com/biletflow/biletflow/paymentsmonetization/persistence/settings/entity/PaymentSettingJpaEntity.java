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

    @Column(name = "amount_kzt", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountKzt;

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

    public void update(BigDecimal amountKzt, long updatedBy, Instant updatedAt) {
        this.amountKzt = amountKzt;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
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
