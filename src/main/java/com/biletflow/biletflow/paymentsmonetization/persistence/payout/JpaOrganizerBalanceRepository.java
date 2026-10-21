package com.biletflow.biletflow.paymentsmonetization.persistence.payout;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentLedger;
import com.biletflow.biletflow.paymentsmonetization.domain.payout.PayoutStatus;
import com.biletflow.biletflow.paymentsmonetization.domain.revenue.*;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.*;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.RefundStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaOrganizerBalanceRepository implements OrganizerBalanceRepository {

    private final EntityManager entities;
    private final PaymentLedger ledger;

    public JpaOrganizerBalanceRepository(EntityManager entities, PaymentLedger ledger) {
        this.entities = Objects.requireNonNull(entities);
        this.ledger = Objects.requireNonNull(ledger);
    }

    @Override
    public OrganizerBalance get(long organizerId) {
        var totals = entities
            .createQuery(
                "select coalesce(sum(case when p.status = :pending then p.amountKzt else 0 end),0), " +
                    "coalesce(sum(case when p.status = :completed then p.amountKzt else 0 end),0) from PayoutJpaEntity p where p.organizerId = :organizer",
                Object[].class
            )
            .setParameter("pending", PayoutStatus.REQUESTED)
            .setParameter("completed", PayoutStatus.COMPLETED)
            .setParameter("organizer", organizerId)
            .getSingleResult();
        boolean blocked =
            entities
                .createQuery(
                    "select count(w) from PaymentWorkJpaEntity w where w.organizerId = :organizer and w.purpose = :ticket and " +
                        "(w.reviewRequired = true or w.operationState = :unknown or w.status = :refundRequested or exists (select r.id from RefundJpaEntity r where r.id = w.currentRefundId and r.status = :requested))",
                    Long.class
                )
                .setParameter("organizer", organizerId)
                .setParameter("ticket", PaymentPurpose.TICKET)
                .setParameter("unknown", PaymentOperationState.UNKNOWN)
                .setParameter("refundRequested", PaymentStatus.REFUND_REQUESTED)
                .setParameter("requested", RefundStatus.REQUESTED)
                .getSingleResult() > 0;
        return OrganizerBalance.fromRevenue(
            organizerId,
            ledger.summary(organizerId).get("estimatedNetKzt"),
            Money.kzt(new BigDecimal(totals[0].toString())),
            Money.kzt(new BigDecimal(totals[1].toString())),
            blocked
        );
    }
}
