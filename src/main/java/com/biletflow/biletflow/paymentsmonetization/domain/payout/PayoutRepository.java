package com.biletflow.biletflow.paymentsmonetization.domain.payout;

import java.util.List;
import java.util.Optional;

public interface PayoutRepository {
    Payout save(Payout payout);
    Optional<Payout> findById(PayoutId id);
    List<Payout> findAllByOrganizerId(Long organizerId, int offset, int limit);
    List<PayoutId> pending(int limit);
}
