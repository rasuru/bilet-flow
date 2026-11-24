package com.biletflow.biletflow.paymentsmonetization.persistence.payment;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentPurpose;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.RefundStatus;
import com.biletflow.biletflow.paymentsmonetization.ohs.RefundAnalyticsOhs;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRefundAnalytics implements RefundAnalyticsOhs {

    private final EntityManager entities;

    public JpaRefundAnalytics(EntityManager entities) {
        this.entities = entities;
    }

    @Override
    public Map<UUID, BigDecimal> confirmedTicketRefunds(UUID eventId) {
        // The payment flow supports full refunds and keeps a history of attempts.
        return entities
            .createQuery(
                """
                select p.orderId, p.amount from PaymentWorkJpaEntity p
                where p.eventId = :eventId and p.purpose = :purpose and p.orderId is not null
                  and exists (select 1 from RefundJpaEntity r where r.paymentId = p.id and r.status = :completed)
                """,
                RefundedOrder.class
            )
            .setParameter("eventId", eventId)
            .setParameter("purpose", PaymentPurpose.TICKET)
            .setParameter("completed", RefundStatus.COMPLETED)
            .getResultList()
            .stream()
            .collect(Collectors.toMap(RefundedOrder::orderId, RefundedOrder::amount));
    }

    public record RefundedOrder(UUID orderId, BigDecimal amount) {}
}
