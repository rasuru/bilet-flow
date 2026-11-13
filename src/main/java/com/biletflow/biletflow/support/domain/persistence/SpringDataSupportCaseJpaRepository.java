package com.biletflow.biletflow.support.persistence.supportcase;

import com.biletflow.biletflow.support.domain.supportcase.CaseStatus;
import com.biletflow.biletflow.support.persistence.supportcase.entity.SupportCaseJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSupportCaseJpaRepository extends JpaRepository<SupportCaseJpaEntity, UUID> {

    List<SupportCaseJpaEntity> findAllByRequesterUserId(Long requesterUserId);

    List<SupportCaseJpaEntity> findAllByAssignedSupportUserId(Long supportUserId);

    List<SupportCaseJpaEntity> findAllByStatus(CaseStatus status);
}
