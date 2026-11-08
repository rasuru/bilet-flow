package com.biletflow.biletflow.paymentsmonetization.domain.payoutaccount;

import java.util.Optional;

public interface PayoutAccountRepository {
    Optional<PayoutAccount> findByOrganizerId(long organizerId);
    PayoutAccount save(PayoutAccount account);
}
