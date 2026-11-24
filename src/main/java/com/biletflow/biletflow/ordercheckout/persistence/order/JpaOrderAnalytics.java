package com.biletflow.biletflow.ordercheckout.persistence.order;

import com.biletflow.biletflow.ordercheckout.ohs.OrderAnalyticsOhs;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOrderAnalytics implements OrderAnalyticsOhs {

    private final EntityManager entities;

    public JpaOrderAnalytics(EntityManager entities) {
        this.entities = entities;
    }

    @Override
    public List<SaleLine> issuedSaleLines(UUID eventId, Instant from, Instant until) {
        // Cancelled orders were issued sales too. Their confirmed refunds are read separately.
        return entities
            .createQuery(
                """
                select o.id, o.createdAt, i.ticketTypeId, count(i.ticketTypeId), sum(i.amount), o.discount
                from OrderJpaEntity o join o.items i
                where o.eventId = :eventId
                  and (:allFrom = true or o.createdAt >= :from)
                  and (:allUntil = true or o.createdAt < :until)
                group by o.id, o.createdAt, i.ticketTypeId, o.discount
                order by o.id, i.ticketTypeId
                """,
                SaleLine.class
            )
            .setParameter("eventId", eventId)
            .setParameter("allFrom", from == null)
            .setParameter("allUntil", until == null)
            .setParameter("from", from)
            .setParameter("until", until)
            .getResultList();
    }
}
