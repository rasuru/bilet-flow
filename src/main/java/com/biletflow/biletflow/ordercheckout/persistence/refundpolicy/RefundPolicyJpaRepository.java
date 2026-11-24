package com.biletflow.biletflow.ordercheckout.persistence.refundpolicy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RefundPolicyJpaRepository extends JpaRepository<RefundPolicyJpaEntity, UUID> {}
