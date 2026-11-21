package com.biletflow.biletflow.support.persistence.supportcase;

import com.biletflow.biletflow.common.application.QueryPage;
import com.biletflow.biletflow.support.application.SupportAdminQueries;
import com.biletflow.biletflow.support.domain.supportcase.CaseStatus;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSupportAdminQueries implements SupportAdminQueries {

    private final EntityManager entities;

    public JpaSupportAdminQueries(EntityManager entities) {
        this.entities = entities;
    }

    @Override
    public List<Summary> search(CaseStatus status, UUID eventId, boolean escalatedOnly, QueryPage page) {
        return entities
            .createQuery(
                """
                select c.id, c.type, c.requesterUserId, c.subject, c.category, c.eventId, c.orderId, c.ticketId, c.status,
                       c.assignedSupportUserId, c.createdAt, c.updatedAt, c.resolvedAt, c.escalatedAt
                from SupportCaseJpaEntity c
                where (c.escalatedAt is not null or (:escalatedOnly = false and c.type = :platform))
                  and c.status = coalesce(:status, c.status)
                  and (:allEvents = true or c.eventId = :eventId)
                order by c.updatedAt desc, c.id desc
                """,
                Summary.class
            )
            .setParameter("platform", SupportCaseType.PLATFORM_SUPPORT)
            .setParameter("escalatedOnly", escalatedOnly)
            .setParameter("status", status)
            .setParameter("allEvents", eventId == null)
            .setParameter("eventId", eventId)
            .setFirstResult(page.offset())
            .setMaxResults(page.limit())
            .getResultList();
    }
}
