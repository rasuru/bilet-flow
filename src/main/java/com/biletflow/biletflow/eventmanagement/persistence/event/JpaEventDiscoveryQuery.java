package com.biletflow.biletflow.eventmanagement.persistence.event;

import com.biletflow.biletflow.eventmanagement.domain.EventVisibility;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventStatus;
import com.biletflow.biletflow.eventmanagement.ohs.EventDiscoveryOhs;
import com.biletflow.biletflow.eventmanagement.ohs.dto.DiscoveryEvent;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEventDiscoveryQuery implements EventDiscoveryOhs {

    private final SpringDataSocialEventJpaRepository events;

    public JpaEventDiscoveryQuery(SpringDataSocialEventJpaRepository events) {
        this.events = Objects.requireNonNull(events);
    }

    @Override
    public List<DiscoveryEvent> findCandidates(Instant now, String query, String category, boolean descending) {
        Sort sort = Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, "eventStartAt").and(Sort.by("id"));
        return events.findDiscoveryCandidates(
            SocialEventStatus.PUBLISHED,
            EventVisibility.PUBLIC,
            now,
            query.toLowerCase(Locale.ROOT),
            category.toLowerCase(Locale.ROOT),
            sort
        );
    }
}
