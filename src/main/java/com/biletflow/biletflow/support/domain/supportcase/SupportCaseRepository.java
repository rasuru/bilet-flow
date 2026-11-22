package com.biletflow.biletflow.support.domain.supportcase;

import java.util.List;
import java.util.Collection;
import java.util.UUID;
import java.util.Optional;

public interface SupportCaseRepository {

    SupportCase save(SupportCase supportCase);

    Optional<SupportCase> findById(SupportCaseId id);

    List<SupportCase> findByRequesterUserId(Long requesterUserId);

    List<SupportCase> findByAssignedSupportUserId(Long supportUserId);

    List<SupportCase> findByEventIdIn(Collection<UUID> eventIds);

    List<SupportCase> findByStatus(CaseStatus status);

    List<SupportCase> findAll();
}
