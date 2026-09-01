package com.biletflow.biletflow.eventmanagement.infrastructure.persistence;

import com.biletflow.biletflow.eventmanagement.infrastructure.persistence.entity.SocialEventEntity;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataSocialEventRepository extends JpaRepository<SocialEventEntity, UUID> {
    List<SocialEventEntity> findByOrganizerId(Long organizerId);
}
