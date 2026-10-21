package com.biletflow.biletflow.paymentsmonetization.domain.revenue;

/** Reads payable funds from authoritative revenue and payout records; no mutable balance copy is stored. */
public interface OrganizerBalanceRepository {
    OrganizerBalance get(long organizerId);
}
