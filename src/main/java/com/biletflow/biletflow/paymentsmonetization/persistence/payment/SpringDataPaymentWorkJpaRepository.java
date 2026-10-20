package com.biletflow.biletflow.paymentsmonetization.persistence.payment;

import com.biletflow.biletflow.paymentsmonetization.persistence.payment.entity.PaymentWorkJpaEntity;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataPaymentWorkJpaRepository extends JpaRepository<PaymentWorkJpaEntity, UUID> {
    Optional<PaymentWorkJpaEntity> findByOrderId(UUID orderId);
    Optional<PaymentWorkJpaEntity> findByInvoiceId(String invoiceId);

    @Query(
        "select p.id from PaymentWorkJpaEntity p where p.nextPoll <= :now and (p.leaseUntil is null or p.leaseUntil <= :now) order by " +
            "case when p.reviewRequired = false and (" +
            "(p.status = com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentStatus.PENDING and p.abandoned = false and p.deadline > :now) " +
            "or p.status = com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentStatus.REFUND_REQUESTED " +
            "or p.operationState in (com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperationState.SENT, com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperationState.UNKNOWN) " +
            "or (p.currentRefundId is not null and p.operationState is null)) then 0 else 1 end, p.nextPoll, p.id"
    )
    List<UUID> due(@Param("now") Instant now, Pageable page);

    @Query(
        "select p.id from PaymentWorkJpaEntity p where p.reviewRequired = true or p.operationState = com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperationState.UNKNOWN order by p.nextPoll, p.id"
    )
    List<UUID> requiringReview(Pageable page);

    @Modifying(flushAutomatically = true)
    @Query(
        "update PaymentWorkJpaEntity p set p.leaseUntil = :until where p.id = :id and p.nextPoll <= :now and (p.leaseUntil is null or p.leaseUntil <= :now)"
    )
    int lease(@Param("id") UUID id, @Param("now") Instant now, @Param("until") Instant until);

    @Modifying(flushAutomatically = true)
    @Query("update PaymentWorkJpaEntity p set p.nextPoll = :when where p.id = :id and p.nextPoll > :when")
    int wake(@Param("id") UUID id, @Param("when") Instant when);

    @Modifying(flushAutomatically = true)
    @Query("update PaymentWorkJpaEntity p set p.nextPoll = :when, p.leaseUntil = null where p.id = :id")
    int schedule(@Param("id") UUID id, @Param("when") Instant when);
}
