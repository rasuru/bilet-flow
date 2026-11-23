package com.biletflow.biletflow.organizerhistory.application;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.common.security.UnauthenticatedActorException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizerHistoryServiceTest {

    private static final long ORGANIZER = 7L;
    private static final long STRANGER = 8L;
    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    private final UUID eventId = UUID.randomUUID();
    private final List<HistoryEntry> eventRows = new ArrayList<>();
    private final List<HistoryEntry> inventoryRows = new ArrayList<>();
    private Long user = ORGANIZER;
    private boolean admin;
    private final List<String> sourceCalls = new ArrayList<>();

    private final CurrentActor actor = new CurrentActor() {
        public Optional<Long> userId() {
            return Optional.ofNullable(user);
        }

        public boolean isAdministrator() {
            return admin;
        }
    };

    private final EventAccessPort access = (event, userId) -> {
        if (!event.equals(eventId)) throw new EntityNotFoundException("Event not found");
        return userId == ORGANIZER;
    };

    private final OrganizerHistoryService service = new OrganizerHistoryService(
        List.of(source("event", eventRows), source("inventory", inventoryRows)),
        access,
        actor
    );

    private HistorySource source(String name, List<HistoryEntry> rows) {
        return (event, from, to) -> {
            sourceCalls.add(name);
            return List.copyOf(rows);
        };
    }

    private HistoryEntry entry(int hours, HistoryCategory category, String action) {
        return new HistoryEntry(UUID.randomUUID(), T0.plusSeconds(hours * 3600L), ORGANIZER, category, action, "EVENT", eventId, action);
    }

    private List<String> actions(HistoryPage page) {
        return page.entries().stream().map(HistoryEntry::action).toList();
    }

    private void sample() {
        eventRows.add(entry(1, HistoryCategory.EVENT, "PUBLISHED"));
        eventRows.add(entry(5, HistoryCategory.EVENT, "CANCELLED"));
        inventoryRows.add(entry(2, HistoryCategory.TICKETING, "TICKET_PRICE_CHANGED"));
        inventoryRows.add(entry(3, HistoryCategory.CHECK_IN, "TICKET_CHECKED_IN"));
        inventoryRows.add(entry(4, HistoryCategory.CHECK_IN, "CHECK_IN_REVERSED"));
    }

    @Test
    void sourcesAreMergedNewestFirst() {
        sample();
        HistoryPage page = service.history(HistoryQuery.all(eventId));
        assertEquals(List.of("CANCELLED", "CHECK_IN_REVERSED", "TICKET_CHECKED_IN", "TICKET_PRICE_CHANGED", "PUBLISHED"), actions(page));
        assertEquals(5, page.total());
        assertEquals(List.of("event", "inventory"), sourceCalls);
    }

    @Test
    void categoryAndActionFiltersCombine() {
        sample();
        assertEquals(
            List.of("CHECK_IN_REVERSED", "TICKET_CHECKED_IN"),
            actions(service.history(new HistoryQuery(eventId, null, null, Set.of(HistoryCategory.CHECK_IN), null, 0, 50)))
        );
        assertEquals(
            List.of("CHECK_IN_REVERSED"),
            actions(
                service.history(
                    new HistoryQuery(eventId, null, null, Set.of(HistoryCategory.CHECK_IN), Set.of(" check_in_reversed "), 0, 50)
                )
            )
        );
        assertEquals(
            List.of("CANCELLED", "TICKET_PRICE_CHANGED"),
            actions(service.history(new HistoryQuery(eventId, null, null, null, Set.of("CANCELLED", "TICKET_PRICE_CHANGED"), 0, 50)))
        );
    }

    @Test
    void dateRangeIncludesFromAndExcludesTo() {
        sample();
        var page = service.history(new HistoryQuery(eventId, T0.plusSeconds(2 * 3600), T0.plusSeconds(4 * 3600), null, null, 0, 50));
        assertEquals(List.of("TICKET_CHECKED_IN", "TICKET_PRICE_CHANGED"), actions(page));
    }

    @Test
    void pagesReportTheFullTotal() {
        sample();
        var page = service.history(new HistoryQuery(eventId, null, null, null, null, 2, 2));
        assertEquals(List.of("PUBLISHED"), actions(page));
        assertEquals(5, page.total());
        assertTrue(
            service
                .history(new HistoryQuery(eventId, null, null, null, null, 9, 2))
                .entries()
                .isEmpty()
        );
    }

    @Test
    void onlyEventManagersAndAdministratorsCanRead() {
        sample();
        user = STRANGER;
        assertThrows(AuthorizationException.class, () -> service.history(HistoryQuery.all(eventId)));
        assertTrue(sourceCalls.isEmpty());

        admin = true;
        assertEquals(5, service.history(HistoryQuery.all(eventId)).total());

        user = null;
        assertThrows(UnauthenticatedActorException.class, () -> service.history(HistoryQuery.all(eventId)));
    }

    @Test
    void unknownEventIsNotFound() {
        assertThrows(EntityNotFoundException.class, () -> service.history(HistoryQuery.all(UUID.randomUUID())));
    }

    @Test
    void invalidQueriesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HistoryQuery(eventId, T0, T0, null, null, 0, 50));
        assertThrows(IllegalArgumentException.class, () -> new HistoryQuery(eventId, T0.plusSeconds(1), T0, null, null, 0, 50));
        assertThrows(IllegalArgumentException.class, () -> new HistoryQuery(eventId, null, null, null, null, -1, 50));
        assertThrows(IllegalArgumentException.class, () -> new HistoryQuery(eventId, null, null, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new HistoryQuery(eventId, null, null, null, null, 0, HistoryQuery.MAX_SIZE + 1));
    }
}
