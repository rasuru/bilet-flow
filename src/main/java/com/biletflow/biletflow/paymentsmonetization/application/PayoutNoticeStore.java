package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.paymentsmonetization.application.view.PayoutView;
import java.time.Instant;
import java.util.*;

public interface PayoutNoticeStore {
    record Claim(UUID token, long organizerId, PayoutView payout) {}

    Optional<Claim> claim(Instant now);
    void complete(Claim claim, Instant now);
    void retry(Claim claim, Instant now);
}
