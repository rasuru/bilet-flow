package com.biletflow.biletflow.ordercheckout.application.refund;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Enqueue joins refund completion. Worker operations commit in short, separate transactions. */
public interface RefundNoticeStore {
    record Claim(RefundNotice notice, UUID token) {}

    void enqueue(RefundNotice notice);
    Optional<Claim> claim(Instant now);
    void complete(Claim claim, Instant now);
    void retry(Claim claim, Instant now);
}
