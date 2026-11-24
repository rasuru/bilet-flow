package com.biletflow.biletflow.organizerhistory.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditRepository.EventAuditType;
import com.biletflow.biletflow.organizerhistory.application.HistoryCategory;
import com.biletflow.biletflow.organizerhistory.application.HistoryQuery;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository.PromotionAuditType;
import com.biletflow.biletflow.ticketinventory.application.audit.TicketInventoryAuditRepository.TicketInventoryAuditType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HistoryActionsTest {

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }

    private static Set<String> covered(HistoryActions actions) {
        Set<String> all = new HashSet<>(actions.shown());
        all.addAll(actions.ignored());
        return all;
    }

    @Test
    void everyEventManagementAuditTypeIsShownOrDeliberatelyIgnored() {
        assertEquals(names(EventAuditType.values()), covered(EventActivityHistorySource.ACTIONS));
        assertEquals(Set.of("SUPPORT_CASE_STATUS_CHANGED"), EventActivityHistorySource.ACTIONS.ignored());
    }

    @Test
    void everyTicketInventoryAuditTypeIsShown() {
        assertEquals(names(TicketInventoryAuditType.values()), TicketInventoryHistorySource.ACTIONS.shown());
    }

    @Test
    void everyPromotionAuditTypeIsShown() {
        assertEquals(names(PromotionAuditType.values()), PromotionsHistorySource.ACTIONS.shown());
    }

    @Test
    void supportStatusChangesComeFromSupportWithTheCaseId() {
        var mapping = SupportHistorySource.ACTIONS.require("CASE_STATUS_CHANGED");
        assertEquals("SUPPORT_CASE_STATUS_CHANGED", mapping.action());
        assertEquals(HistoryActions.Entity.ROW, mapping.entity());
        assertEquals("Support case status changed: OPEN -> RESOLVED", mapping.describe("OPEN -> RESOLVED"));
        assertEquals(Set.of("MESSAGE_ADDED"), SupportHistorySource.ACTIONS.ignored());
    }

    @Test
    void financialActionListsMatchTheMappings() {
        Set<String> listed = new HashSet<>(FinancialHistorySource.REFUND_ACTIONS);
        listed.addAll(FinancialHistorySource.PAID_SALES_ACTIONS);
        listed.addAll(FinancialHistorySource.ACTIVATION_ACTIONS);
        assertEquals(listed, FinancialHistorySource.ACTIONS.shown());
    }

    @Test
    void filtersAreTranslatedToRawTypesBeforeQuerying() {
        UUID eventId = UUID.randomUUID();
        var checkIns = new HistoryQuery(eventId, null, null, Set.of(HistoryCategory.CHECK_IN), null, 0, 50);
        assertEquals(List.of("CHECK_IN_REVERSED", "TICKET_CHECKED_IN"), TicketInventoryHistorySource.ACTIONS.rawTypesFor(checkIns));
        assertTrue(EventActivityHistorySource.ACTIONS.rawTypesFor(checkIns).isEmpty());

        var opened = new HistoryQuery(eventId, null, null, null, Set.of("support_case_opened"), 0, 50);
        assertEquals(List.of("CASE_OPENED"), SupportHistorySource.ACTIONS.rawTypesFor(opened));
    }

    @Test
    void aRawTypeCannotBeBothShownAndIgnored() {
        var mapping = HistoryActions.Mapping.details("X", HistoryCategory.EVENT, "EVENT", HistoryActions.Entity.EVENT);
        assertThrows(IllegalArgumentException.class, () -> new HistoryActions(List.of(mapping), Set.of("X")));
        assertThrows(IllegalArgumentException.class, () -> new HistoryActions(List.of(mapping, mapping), Set.of()));
    }
}
