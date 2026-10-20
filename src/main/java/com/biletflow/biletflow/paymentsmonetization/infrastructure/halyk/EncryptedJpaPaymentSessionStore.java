package com.biletflow.biletflow.paymentsmonetization.infrastructure.halyk;

import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.persistence.JsonCodec;
import com.biletflow.biletflow.paymentsmonetization.application.*;
import com.biletflow.biletflow.paymentsmonetization.persistence.session.entity.PaymentSessionJpaEntity;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class EncryptedJpaPaymentSessionStore implements PaymentSessionStore {

    private final EntityManager entities;
    private final JsonCodec json;
    private final TransactionLocks locks;
    private final TransactionTemplate tx;
    private final String encryptionKey;

    public EncryptedJpaPaymentSessionStore(
        EntityManager entities,
        JsonCodec json,
        TransactionLocks locks,
        PlatformTransactionManager manager,
        @Value("${biletflow.payments.session-encryption-key:}") String encryptionKey
    ) {
        this.entities = Objects.requireNonNull(entities, "entities cannot be null");
        this.json = Objects.requireNonNull(json, "json cannot be null");
        this.locks = Objects.requireNonNull(locks, "locks cannot be null");
        tx = new TransactionTemplate(manager);
        this.encryptionKey = Objects.requireNonNull(encryptionKey, "encryptionKey cannot be null");
    }

    @Override
    public PaymentGateway.Session claim(UUID paymentId, UUID claim, Instant now) {
        byte[] key = key();
        return tx.execute(status -> {
            var row = lockedRow(paymentId);
            if (row != null) {
                if (row.getEncryptedPayload() != null) {
                    if (!now.isBefore(row.getExpiresAt())) throw new IllegalStateException(
                        "Bank form session expired; start a new checkout"
                    );
                    return json.read(crypt(row.getEncryptedPayload(), key, false), PaymentGateway.Session.class);
                }
                if (row.getClaimedUntil().isAfter(now)) throw new IllegalStateException("Payment session is being prepared; retry shortly");
            } else {
                row = new PaymentSessionJpaEntity(paymentId, claim, now.plusSeconds(60), null, null);
                entities.persist(row);
            }
            row.updateMutableState(claim, now.plusSeconds(60), null, null);
            return null;
        });
    }

    @Override
    public void complete(UUID paymentId, UUID claim, PaymentGateway.Session session, Instant deadline, Instant now) {
        var auth = (Map<?, ?>) session.paymentObject().get("auth");
        long seconds = Long.parseLong(String.valueOf(auth.get("expires_in")));
        Instant bankExpiry = now.plusSeconds(Math.max(0, seconds - 30));
        Instant expires = deadline.isBefore(bankExpiry) ? deadline : bankExpiry;
        String encrypted = crypt(json.write(session), key(), true);
        tx.executeWithoutResult(status -> {
            var row = lockedRow(paymentId);
            if (row == null || !claim.equals(row.getClaimId()) || row.getEncryptedPayload() != null) throw new IllegalStateException(
                "Payment session preparation was superseded; retry"
            );
            row.updateMutableState(row.getClaimId(), row.getClaimedUntil(), encrypted, expires);
        });
    }

    @Override
    public void release(UUID paymentId, UUID claim) {
        tx.executeWithoutResult(status -> {
            var row = lockedRow(paymentId);
            if (row != null && claim.equals(row.getClaimId()) && row.getEncryptedPayload() == null) entities.remove(row);
        });
    }

    private PaymentSessionJpaEntity lockedRow(UUID paymentId) {
        locks.lock("payment-session:" + paymentId);
        var row = entities.find(PaymentSessionJpaEntity.class, paymentId);
        if (row != null) entities.refresh(row);
        return row;
    }

    private byte[] key() {
        try {
            byte[] bytes = Base64.getDecoder().decode(encryptionKey);
            if (bytes.length != 32) throw new IllegalArgumentException();
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Configure a stable base64 32-byte BILETFLOW_PAYMENTS_SESSION_ENCRYPTION_KEY");
        }
    }

    private String crypt(String value, byte[] key, boolean encrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] nonce = new byte[12];
            if (encrypt) {
                new SecureRandom().nextBytes(nonce);
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
                byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
                byte[] output = new byte[12 + ciphertext.length];
                System.arraycopy(nonce, 0, output, 0, 12);
                System.arraycopy(ciphertext, 0, output, 12, ciphertext.length);
                return Base64.getEncoder().encodeToString(output);
            }
            byte[] input = Base64.getDecoder().decode(value);
            System.arraycopy(input, 0, nonce, 0, 12);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(input, 12, input.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Payment session encryption failed");
        }
    }
}
