package com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketOrderPaymentRepository {
    TicketOrderPayment save(TicketOrderPayment ticketOrderPayment);

    Optional<TicketOrderPayment> findById(TicketOrderPaymentId id);

    /**
     * The settled payment for this order, if the order has already been charged.
     * Settled means SUCCEEDED, PARTIALLY_REFUNDED or FULLY_REFUNDED — a later
     * refund does not make an order uncharged.
     */

    Optional<TicketOrderPayment> findByRefundId(RefundId refundId);

    List<TicketOrderPayment> findAllByOrderId(UUID orderId);
}
