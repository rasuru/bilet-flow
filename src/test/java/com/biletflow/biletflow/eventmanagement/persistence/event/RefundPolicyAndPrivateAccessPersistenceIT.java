package com.biletflow.biletflow.eventmanagement.persistence.event;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.eventmanagement.domain.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class RefundPolicyAndPrivateAccessPersistenceIT {

    @Autowired
    SocialEventRepository events;

    @Autowired
    PlatformTransactionManager manager;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private final List<SocialEventId> saved = new ArrayList<>();

    private TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }

    @AfterEach
    void cleanup() {
        tx().executeWithoutResult(status -> saved.forEach(events::deleteById));
    }

    private SocialEvent draft(EventVisibility visibility) {
        var event = SocialEvent.createDraft(
            77L,
            "Persisted",
            "",
            "Music",
            "",
            visibility,
            new EventDateRange(now.plusSeconds(86_400), now.plusSeconds(90_000)),
            new RegistrationWindow.AlwaysOpen()
        );
        saved.add(event.getId());
        return event;
    }

    private SocialEvent reload(SocialEventId id) {
        return tx().execute(status -> events.findById(id).orElseThrow());
    }

    @Test
    void refundPolicyAndAccessCodeSurviveAReload() {
        var event = draft(EventVisibility.PRIVATE);
        event.defineRefundPolicy(new RefundPolicy(true, 48, "Email the organizer"));
        tx().executeWithoutResult(status -> events.save(event));

        var loaded = reload(event.getId());

        assertEquals(new RefundPolicy(true, 48, "Email the organizer"), loaded.getRefundPolicy().orElseThrow());
        assertEquals(event.getPrivateAccessCode(), loaded.getPrivateAccessCode());
        assertTrue(loaded.grantsAttendeeAccess(event.getPrivateAccessCode().orElseThrow().value()));
        assertFalse(loaded.grantsAttendeeAccess(null));
    }

    @Test
    void removedPolicyAndRotatedCodeArePersisted() {
        var event = draft(EventVisibility.PRIVATE);
        event.defineRefundPolicy(new RefundPolicy(false, 0, null));
        tx().executeWithoutResult(status -> events.save(event));

        String rotated = tx().execute(status -> {
            var loaded = events.findById(event.getId()).orElseThrow();
            loaded.removeRefundPolicy();
            String code = loaded.rotatePrivateAccessCode().value();
            events.save(loaded);
            return code;
        });

        var reloaded = reload(event.getId());
        assertTrue(reloaded.getRefundPolicy().isEmpty());
        assertEquals(rotated, reloaded.getPrivateAccessCode().orElseThrow().value());
    }

    @Test
    void publicEventStoresNoAccessCode() {
        var event = draft(EventVisibility.PUBLIC);
        tx().executeWithoutResult(status -> events.save(event));

        var loaded = reload(event.getId());
        assertTrue(loaded.getPrivateAccessCode().isEmpty());
        assertTrue(loaded.getRefundPolicy().isEmpty());
    }
}
