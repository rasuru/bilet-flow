package com.biletflow.biletflow.paymentsmonetization.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentWorkRepository {
    String nextInvoice();
    Optional<PaymentWork> find(UUID id);
    Optional<PaymentWork> findOrder(UUID orderId);
    Optional<PaymentWork> findInvoice(String invoiceId);
    void insert(PaymentWork work);
    void save(PaymentWork work);
    List<UUID> due(Instant now);
    List<UUID> requiringReview();
    boolean lease(UUID id, Instant now, Instant until);
    void schedule(UUID id, Instant when);
    void wake(UUID id, Instant when);
}
