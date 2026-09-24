package com.biletflow.biletflow.payments.domain.revenue;

import java.util.Optional;

public interface OrganizerBalanceRepository {
    OrganizerBalance save(OrganizerBalance balance);

    Optional<OrganizerBalance> findByOrganizerId(Long organizerId);
}
