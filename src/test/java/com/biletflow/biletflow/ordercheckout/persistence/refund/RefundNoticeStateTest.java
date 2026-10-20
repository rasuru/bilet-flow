package com.biletflow.biletflow.ordercheckout.persistence.refund;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.ordercheckout.application.refund.RefundNotice;
import com.biletflow.biletflow.ordercheckout.persistence.refund.entity.RefundNoticeJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundNoticeStateTest {

    @Test
    void recoversExpiredLeaseIgnoresStaleWorkersAndBacksOffBeforeRetry() {
        var now = Instant.parse("2026-10-20T12:00:00Z");
        var n = new RefundNotice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 42, "Event", new BigDecimal("1000.00"), now);
        var row = new RefundNoticeJpaEntity(n);
        var old = row.claim(now);
        assertThrows(IllegalStateException.class, () -> row.claim(now.plusSeconds(119)));
        var replacement = row.claim(now.plusSeconds(120));
        assertEquals(n, replacement.notice());
        row.complete(old, now.plusSeconds(121));
        row.retry(old, now.plusSeconds(121));
        assertThrows(IllegalStateException.class, () -> row.claim(now.plusSeconds(122)));
        row.retry(replacement, now.plusSeconds(122));
        assertThrows(IllegalStateException.class, () -> row.claim(now.plusSeconds(181)));
        var retry = row.claim(now.plusSeconds(182));
        assertEquals(n, retry.notice());
        row.complete(retry, now.plusSeconds(183));
        assertThrows(IllegalStateException.class, () -> row.claim(now.plusSeconds(10000)));
    }
}
