package com.biletflow.biletflow.eventmanagement.application.cancellation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CancellationNoticeStore {
    record Claim(CancellationNotice notice, UUID token) {}

    void enqueue(CancellationNotice notice);
    Optional<Claim> claim(Instant now);
    void complete(Claim claim, Instant now);
    void retry(Claim claim, Instant now);
}
