package com.biletflow.biletflow.eventmanagement.persistence.event;

import com.biletflow.biletflow.common.application.QueryPage;
import com.biletflow.biletflow.eventmanagement.application.socialevent.EventAdminQueries;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEventAdminQueries implements EventAdminQueries {

    private final EntityManager entities;

    public JpaEventAdminQueries(EntityManager entities) {
        this.entities = entities;
    }

    @Override
    public List<Summary> search(Filter filter, QueryPage page) {
        return entities
            .createQuery(
                """
                select e.id, e.organizerId, e.title, e.category, e.status, e.visibility, e.eventStartAt, e.eventEndAt
                from SocialEventJpaEntity e
                where e.organizerId = coalesce(:organizerId, e.organizerId)
                  and e.status = coalesce(:status, e.status)
                  and e.visibility = coalesce(:visibility, e.visibility)
                  and (:search = '' or locate(lower(:search), lower(e.title)) > 0 or locate(lower(:search), lower(e.category)) > 0)
                order by e.eventStartAt desc, e.id desc
                """,
                Summary.class
            )
            .setParameter("organizerId", filter.organizerId())
            .setParameter("status", filter.status())
            .setParameter("visibility", filter.visibility())
            .setParameter("search", filter.search() == null ? "" : filter.search().strip())
            .setFirstResult(page.offset())
            .setMaxResults(page.limit())
            .getResultList();
    }
}
