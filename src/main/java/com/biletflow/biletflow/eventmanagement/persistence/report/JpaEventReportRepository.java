package com.biletflow.biletflow.eventmanagement.persistence.report;

import com.biletflow.biletflow.common.application.QueryPage;
import com.biletflow.biletflow.eventmanagement.application.socialevent.EventReportQueries;
import com.biletflow.biletflow.eventmanagement.domain.report.EventReport;
import com.biletflow.biletflow.eventmanagement.domain.report.EventReportRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEventReportRepository implements EventReportRepository, EventReportQueries {

    private final EntityManager entities;

    public JpaEventReportRepository(EntityManager entities) {
        this.entities = entities;
    }

    @Override
    public Optional<EventReport> find(UUID id) {
        return Optional.ofNullable(entities.find(EventReportJpaEntity.class, id)).map(EventReportJpaEntity::toDomain);
    }

    @Override
    public Optional<EventReport> findByReporter(UUID eventId, long reporterId) {
        return entities
            .createQuery(
                "select r from EventReportJpaEntity r where r.eventId = :eventId and r.reporterId = :reporterId",
                EventReportJpaEntity.class
            )
            .setParameter("eventId", eventId)
            .setParameter("reporterId", reporterId)
            .getResultList()
            .stream()
            .findFirst()
            .map(EventReportJpaEntity::toDomain);
    }

    @Override
    public void save(EventReport report) {
        var row = entities.find(EventReportJpaEntity.class, report.id());
        if (row == null) {
            entities.persist(new EventReportJpaEntity(report));
        } else {
            row.updateReview(report);
        }
    }

    @Override
    public List<View> search(UUID eventId, EventReport.Status status, QueryPage page) {
        return entities
            .createQuery(
                """
                select r from EventReportJpaEntity r
                where r.eventId = coalesce(:eventId, r.eventId) and r.status = coalesce(:status, r.status)
                order by r.submittedAt desc, r.id desc
                """,
                EventReportJpaEntity.class
            )
            .setParameter("eventId", eventId)
            .setParameter("status", status)
            .setFirstResult(page.offset())
            .setMaxResults(page.limit())
            .getResultList()
            .stream()
            .map(row -> View.from(row.toDomain()))
            .toList();
    }
}
