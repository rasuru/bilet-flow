package com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketOrderPaymentRepository {
    TicketOrderPayment save(TicketOrderPayment ticketOrderPayment);

    Optional<TicketOrderPayment> findById(TicketOrderPaymentId id);

    Optional<TicketOrderPayment> findByRefundId(RefundId refundId);

    List<TicketOrderPayment> findAllByOrderId(UUID orderId);
}
