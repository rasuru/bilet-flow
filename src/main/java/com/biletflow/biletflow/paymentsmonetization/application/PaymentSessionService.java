package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.persistence.JsonCodec;
import com.biletflow.biletflow.common.persistence.TransactionLocks;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** One reusable browser session per invoice; the short-lived bank form token is encrypted at rest. */
@Component
public class PaymentSessionService {

    private final JdbcTemplate jdbc;
    private final JsonCodec json;
    private final PaymentGateway bank;
    private final TransactionLocks locks;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final String encryptionKey;

    public PaymentSessionService(
        JdbcTemplate jdbc,
        JsonCodec json,
        PaymentGateway bank,
        TransactionLocks locks,
        Clock clock,
        PlatformTransactionManager manager,
        @Value("${biletflow.payments.session-encryption-key:}") String encryptionKey
    ) {
        this.jdbc = jdbc;
        this.json = json;
        this.bank = bank;
        this.locks = locks;
        this.clock = clock;
        this.tx = new TransactionTemplate(manager);
        this.encryptionKey = encryptionKey;
    }

    private record Cached(UUID claim, String encrypted, Instant expiresAt, Instant claimedUntil) {}

    public PaymentGateway.Session open(PaymentWork work) {
        byte[] key = key();
        UUID claim = UUID.randomUUID();
        PaymentGateway.Session cached = tx.execute(s -> {
            locks.lock("payment-session:" + work.id);
            var rows = jdbc.query(
                "select claim_id,encrypted_payload,expires_at,claimed_until from pm_payment_session where payment_id=?",
                (rs, n) ->
                    new Cached(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4).toInstant()
                    ),
                work.id
            );
            if (!rows.isEmpty()) {
                var row = rows.getFirst();
                if (row.encrypted() != null) {
                    if (!clock.instant().isBefore(row.expiresAt())) throw new IllegalStateException(
                        "Bank form session expired; start a new checkout"
                    );
                    return json.read(crypt(row.encrypted(), key, false), PaymentGateway.Session.class);
                }
                if (row.claimedUntil().isAfter(clock.instant())) throw new IllegalStateException(
                    "Payment session is being prepared; retry shortly"
                );
            }
            jdbc.update(
                "insert into pm_payment_session(payment_id,claim_id,claimed_until) values (?,?,?) on conflict(payment_id) do update set claim_id=excluded.claim_id,claimed_until=excluded.claimed_until",
                work.id,
                claim,
                Timestamp.from(clock.instant().plusSeconds(60))
            );
            return null;
        });
        if (cached != null) return cached;
        try {
            var session = bank.session(work);
            var auth = (Map<?, ?>) session.paymentObject().get("auth");
            long seconds = Long.parseLong(String.valueOf(auth.get("expires_in")));
            Instant expires = clock.instant().plusSeconds(Math.max(0, seconds - 30));
            if (work.deadline.isBefore(expires)) expires = work.deadline;
            String encrypted = crypt(json.write(session), key, true);
            Instant finalExpiry = expires;
            int updated = tx.execute(s ->
                jdbc.update(
                    "update pm_payment_session set encrypted_payload=?,expires_at=? where payment_id=? and claim_id=?",
                    encrypted,
                    Timestamp.from(finalExpiry),
                    work.id,
                    claim
                )
            );
            if (updated != 1) throw new IllegalStateException("Payment session preparation was superseded; retry");
            return session;
        } catch (RuntimeException failure) {
            tx.executeWithoutResult(s ->
                jdbc.update(
                    "delete from pm_payment_session where payment_id=? and claim_id=? and encrypted_payload is null",
                    work.id,
                    claim
                )
            );
            throw failure;
        }
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
