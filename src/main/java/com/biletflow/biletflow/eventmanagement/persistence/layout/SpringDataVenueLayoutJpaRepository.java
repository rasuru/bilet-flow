package com.biletflow.biletflow.eventmanagement.persistence.layout;

import com.biletflow.biletflow.eventmanagement.persistence.layout.entity.VenueLayoutJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataVenueLayoutJpaRepository extends JpaRepository<VenueLayoutJpaEntity, UUID> {}
