package com.biletflow.biletflow.paymentsmonetization.persistence.payoutaccount.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "pm_payout_account")
public class PayoutAccountJpaEntity {

    @Id
    @Column(name = "organizer_id", nullable = false, updatable = false)
    private long organizerId;

    @Column(name = "holder_name", nullable = false, length = 100)
    private String holderName;

    @Column(name = "bank_code", nullable = false, length = 3)
    private String bankCode;

    @Column(name = "iban_last_four", nullable = false, length = 4)
    private String ibanLastFour;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PayoutAccountJpaEntity() {}

    public PayoutAccountJpaEntity(long organizerId, Instant registeredAt) {
        this.organizerId = organizerId;
        this.registeredAt = registeredAt;
    }

    public void updateMutableState(String holderName, String bankCode, String ibanLastFour, Instant updatedAt) {
        this.holderName = holderName;
        this.bankCode = bankCode;
        this.ibanLastFour = ibanLastFour;
        this.updatedAt = updatedAt;
    }

    public long getOrganizerId() {
        return organizerId;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getBankCode() {
        return bankCode;
    }

    public String getIbanLastFour() {
        return ibanLastFour;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
