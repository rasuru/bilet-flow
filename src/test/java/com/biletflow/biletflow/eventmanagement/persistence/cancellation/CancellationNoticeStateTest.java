package com.biletflow.biletflow.eventmanagement.persistence.cancellation;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.eventmanagement.application.cancellation.CancellationNotice;
import com.biletflow.biletflow.eventmanagement.persistence.cancellation.entity.CancellationNoticeJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CancellationNoticeStateTest {

    @Test
    void expiredLeaseCanBeRecoveredAndOldWorkerCannotAcknowledgeOrRescheduleIt() {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        var row = new CancellationNoticeJpaEntity(
            CancellationNotice.forRecipient(UUID.randomUUID(), "a@example.com", "Event", now.plusSeconds(3600), now.plusSeconds(7200), now)
        );
        var first = row.claim(now);
        assertThrows(IllegalStateException.class, () -> row.claim(now.plusSeconds(119)));
        var replacement = row.claim(now.plusSeconds(121));
        assertEquals(first.notice().id(), replacement.notice().id());
        assertNotEquals(first.token(), replacement.token());
        row.complete(first, now.plusSeconds(122));
        row.retry(first, now.plusSeconds(122));
        row.retry(replacement, now.plusSeconds(122));
        assertThrows(IllegalStateException.class, () -> row.claim(now.plusSeconds(181)));
        var retried = row.claim(now.plusSeconds(182));
        row.complete(retried, now.plusSeconds(183));
        assertThrows(IllegalStateException.class, () -> row.claim(now.plusSeconds(10000)));
    }
}
