package com.biletflow.biletflow.payments.domain.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findById(PaymentId id);

    /**
     * The settled payment for this order, if the order has already been charged.
     * Settled means SUCCEEDED, PARTIALLY_REFUNDED or FULLY_REFUNDED — a later
     * refund does not make an order uncharged.
     */
    Optional<Payment> findSettledByOrderId(UUID orderId);

    List<Payment> findAllByOrderId(UUID orderId);
}
