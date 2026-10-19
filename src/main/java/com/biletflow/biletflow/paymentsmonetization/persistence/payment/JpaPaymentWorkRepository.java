package com.biletflow.biletflow.paymentsmonetization.persistence.payment;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentWorkRepository;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import com.biletflow.biletflow.paymentsmonetization.persistence.payment.entity.HalykInvoiceJpaEntity;
import com.biletflow.biletflow.paymentsmonetization.persistence.payment.entity.PaymentWorkJpaEntity;
import com.biletflow.biletflow.paymentsmonetization.persistence.payment.entity.RefundJpaEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(readOnly = true)
public class JpaPaymentWorkRepository implements PaymentWorkRepository {

    private final EntityManager entities;
    private final SpringDataPaymentWorkJpaRepository rows;
    private final PaymentPersistenceMapper mapper;

    public JpaPaymentWorkRepository(EntityManager entities, SpringDataPaymentWorkJpaRepository rows, PaymentPersistenceMapper mapper) {
        this.entities = Objects.requireNonNull(entities, "entities cannot be null");
        this.rows = Objects.requireNonNull(rows, "rows cannot be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
    }

    @Override
    @Transactional
    public String nextInvoice() {
        var allocation = HalykInvoiceJpaEntity.allocate();
        entities.persist(allocation);
        return String.format(Locale.ROOT, "%06d", allocation.getId());
    }

    @Override
    public Optional<PaymentWork> find(UUID id) {
        var row = entities.find(PaymentWorkJpaEntity.class, id);
        if (row == null) return Optional.empty();
        // An initial read can precede an advisory lock. Reload after lock acquisition rather than reuse stale managed state.
        entities.flush();
        entities.refresh(row);
        return Optional.of(domain(row));
    }

    @Override
    public Optional<PaymentWork> findOrder(UUID id) {
        return rows.findByOrderId(id).map(this::domain);
    }

    @Override
    public Optional<PaymentWork> findInvoice(String id) {
        return rows.findByInvoiceId(id).map(this::domain);
    }

    private PaymentWork domain(PaymentWorkJpaEntity row) {
        var refund = row.getCurrentRefundId() == null ? null : entities.find(RefundJpaEntity.class, row.getCurrentRefundId());
        if (row.getCurrentRefundId() != null && refund == null) throw new IllegalStateException("Current refund is missing");
        if (refund != null) {
            entities.flush();
            entities.refresh(refund);
        }
        if (refund != null && !row.getId().equals(refund.getPaymentId())) throw new IllegalStateException(
            "Refund belongs to a different payment"
        );
        return mapper.toDomain(row, refund);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(PaymentWork work) {
        var row = mapper.toNewEntity(work);
        entities.persist(row);
        entities.flush();
        // Activation and ledger rows reference the newly created payment.
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(PaymentWork work) {
        var row = entities.find(PaymentWorkJpaEntity.class, work.getId());
        if (row == null) throw new IllegalStateException("Payment not found");
        if (work.isTicket()) work.getPayment()
            .getRefund()
            .ifPresent(refund -> {
                var attempt = entities.find(RefundJpaEntity.class, refund.getId().value());
                boolean created = attempt == null;
                if (created) attempt = mapper.toNewEntity(refund, work.getId());
                else mapper.updateEntity(refund, work.getId(), attempt);
                if (created) entities.persist(attempt);
            });
        mapper.updateEntity(work, row);
    }

    @Override
    public List<UUID> due(Instant now) {
        return rows.due(now, PageRequest.of(0, 50));
    }

    @Override
    public List<UUID> requiringReview() {
        return rows.requiringReview(PageRequest.of(0, 200));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lease(UUID id, Instant now, Instant until) {
        return rows.lease(id, now, until) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void wake(UUID id, Instant when) {
        rows.wake(id, when);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void schedule(UUID id, Instant when) {
        rows.schedule(id, when);
    }
}
