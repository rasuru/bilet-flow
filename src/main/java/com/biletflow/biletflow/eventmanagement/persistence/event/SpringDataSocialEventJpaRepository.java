package com.biletflow.biletflow.eventmanagement.persistence.event;

import com.biletflow.biletflow.eventmanagement.domain.EventVisibility;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventStatus;
import com.biletflow.biletflow.eventmanagement.persistence.event.entity.SocialEventJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSocialEventJpaRepository extends JpaRepository<SocialEventJpaEntity, UUID> {
    List<SocialEventJpaEntity> findAllByOrganizerId(Long organizerId);

    List<SocialEventJpaEntity> findAllByStatusAndVisibility(SocialEventStatus status, EventVisibility visibility);
}
