package com.biletflow.biletflow.paymentsmonetization.infrastructure.halyk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "pm_payment_session")
public class PaymentSessionJpaEntity {

    @Id
    @Column(name = "payment_id")
    UUID paymentId;

    @Column(name = "claim_id", nullable = false)
    UUID claimId;

    @Column(name = "claimed_until", nullable = false)
    Instant claimedUntil;

    @Column(name = "encrypted_payload", columnDefinition = "text")
    String encryptedPayload;

    @Column(name = "expires_at")
    Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    long version;

    protected PaymentSessionJpaEntity() {}
}
