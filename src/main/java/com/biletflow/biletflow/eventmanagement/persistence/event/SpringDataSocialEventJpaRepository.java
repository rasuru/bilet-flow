package com.biletflow.biletflow.eventmanagement.persistence.event;

import com.biletflow.biletflow.eventmanagement.domain.EventVisibility;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventStatus;
import com.biletflow.biletflow.eventmanagement.ohs.dto.DiscoveryEvent;
import com.biletflow.biletflow.eventmanagement.persistence.event.entity.SocialEventJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataSocialEventJpaRepository extends JpaRepository<SocialEventJpaEntity, UUID> {
    List<SocialEventJpaEntity> findAllByOrganizerId(Long organizerId);

    @Query("select distinct e from SocialEventJpaEntity e join e.staffAssignments s where s.userId = :userId")
    List<SocialEventJpaEntity> findAllByStaffUserId(@Param("userId") Long userId);

    @Query("""
        select new com.biletflow.biletflow.eventmanagement.ohs.dto.DiscoveryEvent(
            e.id, e.title, e.category, e.eventStartAt, e.imageUrl, e.venueName, e.venueAddress, e.registrationStartAt
        )
        from SocialEventJpaEntity e
        where e.status = :status and e.visibility = :visibility and e.eventEndAt >= :now
          and (e.registrationEndAt is null or e.registrationEndAt >= :now)
          and (:category = '' or lower(e.category) = :category)
          and (:query = '' or locate(:query, lower(e.title)) > 0 or locate(:query, lower(e.description)) > 0)
        """)
    List<DiscoveryEvent> findDiscoveryCandidates(
        @Param("status") SocialEventStatus status,
        @Param("visibility") EventVisibility visibility,
        @Param("now") Instant now,
        @Param("query") String query,
        @Param("category") String category,
        Sort sort
    );
}
