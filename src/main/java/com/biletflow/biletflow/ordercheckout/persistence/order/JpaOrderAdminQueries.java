package com.biletflow.biletflow.ordercheckout.persistence.order;

import com.biletflow.biletflow.common.application.QueryPage;
import com.biletflow.biletflow.ordercheckout.application.OrderAdminQueries;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOrderAdminQueries implements OrderAdminQueries {

    private final EntityManager entities;

    public JpaOrderAdminQueries(EntityManager entities) {
        this.entities = entities;
    }

    @Override
    public List<View> search(Filter filter, QueryPage page) {
        return entities
            .createQuery(
                """
                select o.id, o.eventId, o.ownerId, o.status, o.promotionId, o.discount, o.discountCurrency,
                       o.total, o.totalCurrency, o.paymentId, o.refundId
                from OrderJpaEntity o
                where o.id = coalesce(:orderId, o.id)
                  and o.eventId = coalesce(:eventId, o.eventId)
                  and o.ownerId = coalesce(:ownerId, o.ownerId)
                  and o.status = coalesce(:status, o.status)
                  and (:allPromotions = true or o.promotionId = :promotionId)
                  and (:promotedOnly = false or o.promotionId is not null)
                order by o.id
                """,
                View.class
            )
            .setParameter("orderId", filter.orderId())
            .setParameter("eventId", filter.eventId())
            .setParameter("ownerId", filter.ownerId())
            .setParameter("status", filter.status())
            .setParameter("allPromotions", filter.promotionId() == null)
            .setParameter("promotionId", filter.promotionId())
            .setParameter("promotedOnly", filter.promotedOnly())
            .setFirstResult(page.offset())
            .setMaxResults(page.limit())
            .getResultList();
    }
}
