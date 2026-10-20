package com.biletflow.biletflow.paymentsmonetization.application;

import java.time.Instant;
import java.util.UUID;

/** Each operation is atomic. Implementations protect stored credentials and serialize claims. */
public interface PaymentSessionStore {
    PaymentGateway.Session claim(UUID paymentId, UUID claim, Instant now);
    void complete(UUID paymentId, UUID claim, PaymentGateway.Session session, Instant deadline, Instant now);
    void release(UUID paymentId, UUID claim);
}
