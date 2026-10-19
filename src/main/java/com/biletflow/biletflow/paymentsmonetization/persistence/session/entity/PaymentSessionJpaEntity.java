package com.biletflow.biletflow.paymentsmonetization.persistence.session.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "pm_payment_session")
public class PaymentSessionJpaEntity {

    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "claimed_until", nullable = false)
    private Instant claimedUntil;

    @Column(name = "encrypted_payload", columnDefinition = "text")
    private String encryptedPayload;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PaymentSessionJpaEntity() {}

    public PaymentSessionJpaEntity(UUID paymentId, UUID claimId, Instant claimedUntil, String encryptedPayload, Instant expiresAt) {
        this.paymentId = paymentId;
        this.claimId = claimId;
        this.claimedUntil = claimedUntil;
        this.encryptedPayload = encryptedPayload;
        this.expiresAt = expiresAt;
    }

    public void updateMutableState(UUID claimId, Instant claimedUntil, String encryptedPayload, Instant expiresAt) {
        this.claimId = claimId;
        this.claimedUntil = claimedUntil;
        this.encryptedPayload = encryptedPayload;
        this.expiresAt = expiresAt;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public Instant getClaimedUntil() {
        return claimedUntil;
    }

    public String getEncryptedPayload() {
        return encryptedPayload;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
