package com.biletflow.biletflow.eventmanagement.persistence.report;

import com.biletflow.biletflow.eventmanagement.domain.report.EventReport;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_management_report")
public class EventReportJpaEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private long reporterId;

    @Column(nullable = false, updatable = false, length = 1000)
    private String reason;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventReport.Status status;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Version
    @Column(nullable = false)
    private long version;

    protected EventReportJpaEntity() {}

    EventReportJpaEntity(EventReport report) {
        id = report.id();
        eventId = report.eventId();
        reporterId = report.reporterId();
        reason = report.reason();
        submittedAt = report.submittedAt();
        updateReview(report);
    }

    void updateReview(EventReport report) {
        status = report.status();
        reviewerId = report.reviewerId();
        reviewedAt = report.reviewedAt();
        reviewNote = report.reviewNote();
    }

    EventReport toDomain() {
        return new EventReport(id, eventId, reporterId, reason, submittedAt, status, reviewerId, reviewedAt, reviewNote);
    }
}
