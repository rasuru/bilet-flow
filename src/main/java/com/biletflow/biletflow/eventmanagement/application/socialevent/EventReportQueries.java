package com.biletflow.biletflow.eventmanagement.application.socialevent;

import com.biletflow.biletflow.common.application.QueryPage;
import com.biletflow.biletflow.eventmanagement.domain.report.EventReport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventReportQueries {
    record View(
        UUID id,
        UUID eventId,
        long reporterId,
        String reason,
        Instant submittedAt,
        EventReport.Status status,
        Long reviewerId,
        Instant reviewedAt,
        String reviewNote
    ) {
        public static View from(EventReport report) {
            return new View(
                report.id(), report.eventId(), report.reporterId(), report.reason(), report.submittedAt(),
                report.status(), report.reviewerId(), report.reviewedAt(), report.reviewNote()
            );
        }
    }

    List<View> search(UUID eventId, EventReport.Status status, QueryPage page);
}
