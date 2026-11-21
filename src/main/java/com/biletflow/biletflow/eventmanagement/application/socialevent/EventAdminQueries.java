package com.biletflow.biletflow.eventmanagement.application.socialevent;

import com.biletflow.biletflow.common.application.QueryPage;
import com.biletflow.biletflow.eventmanagement.domain.EventVisibility;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventAdminQueries {
    record Filter(String search, SocialEventStatus status, EventVisibility visibility, Long organizerId) {}

    record Summary(
        UUID id,
        Long organizerId,
        String title,
        String category,
        SocialEventStatus status,
        EventVisibility visibility,
        Instant startsAt,
        Instant endsAt
    ) {}

    List<Summary> search(Filter filter, QueryPage page);
}
