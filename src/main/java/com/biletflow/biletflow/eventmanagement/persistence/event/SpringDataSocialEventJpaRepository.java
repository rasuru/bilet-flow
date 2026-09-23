package com.biletflow.biletflow.eventmanagement.persistence.event;

import com.biletflow.biletflow.eventmanagement.domain.EventVisibility;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventStatus;
import com.biletflow.biletflow.eventmanagement.persistence.event.entity.SocialEventJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataSocialEventJpaRepository extends JpaRepository<SocialEventJpaEntity, UUID> {
    List<SocialEventJpaEntity> findAllByOrganizerId(Long organizerId);

    @Query("select distinct e from SocialEventJpaEntity e join e.staffAssignments s where s.userId = :userId")
    List<SocialEventJpaEntity> findAllByStaffUserId(@Param("userId") Long userId);

    List<SocialEventJpaEntity> findAllByStatusAndVisibility(SocialEventStatus status, EventVisibility visibility);
}
