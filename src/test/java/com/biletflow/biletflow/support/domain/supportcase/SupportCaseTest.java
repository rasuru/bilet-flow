package com.biletflow.biletflow.support.domain.supportcase;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.support.domain.exceptions.InvalidSupportCaseStateException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupportCaseTest {

    private static final Instant OPENED_AT = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant REPLIED_AT = OPENED_AT.plusSeconds(60);
    private static final Instant RESOLVED_AT = OPENED_AT.plusSeconds(120);

    private SupportCase openCase() {
        return SupportCase.open(
            10L,
            "Ticket was not delivered",
            IssueCategory.TICKET,
            "I paid for my ticket but cannot find it.",
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            OPENED_AT
        );
    }

    @Test
    void openingCreatesAnOpenCaseWithInitialRequesterMessage() {
        SupportCase supportCase = openCase();

        assertNotNull(supportCase.getId());
        assertEquals(10L, supportCase.getRequesterUserId());
        assertEquals("Ticket was not delivered", supportCase.getSubject());
        assertEquals(IssueCategory.TICKET, supportCase.getCategory());
        assertEquals(CaseStatus.OPEN, supportCase.getStatus());
        assertNull(supportCase.getAssignedSupportUserId());
        assertEquals(OPENED_AT, supportCase.getCreatedAt());
        assertEquals(OPENED_AT, supportCase.getUpdatedAt());
        assertEquals(1, supportCase.getMessages().size());
        assertEquals(10L, supportCase.getMessages().getFirst().authorUserId());
        assertEquals(MessageAuthorType.REQUESTER, supportCase.getMessages().getFirst().authorType());
    }

    @Test
    void openingRejectsInvalidRequesterSubjectAndInitialMessage() {
        assertThrows(NullPointerException.class, () ->
            SupportCase.open(null, "Subject", IssueCategory.GENERAL, "Message", null, null, null, OPENED_AT)
        );
        assertThrows(IllegalArgumentException.class, () ->
            SupportCase.open(10L, "  ", IssueCategory.GENERAL, "Message", null, null, null, OPENED_AT)
        );
        assertThrows(IllegalArgumentException.class, () ->
            SupportCase.open(10L, "Subject", IssueCategory.GENERAL, "  ", null, null, null, OPENED_AT)
        );
    }

    @Test
    void assigningAnOpenCaseMovesItToInProgress() {
        SupportCase supportCase = openCase();

        supportCase.assignTo(20L, REPLIED_AT);

        assertEquals(CaseStatus.IN_PROGRESS, supportCase.getStatus());
        assertEquals(20L, supportCase.getAssignedSupportUserId());
        assertEquals(REPLIED_AT, supportCase.getAssignedAt());
        assertEquals(REPLIED_AT, supportCase.getUpdatedAt());
    }

    @Test
    void supportReplyMovesCaseToWaitingForCustomer() {
        SupportCase supportCase = openCase();
        supportCase.assignTo(20L, REPLIED_AT);

        supportCase.reply(20L, MessageAuthorType.SUPPORT_AGENT, "Please check your email inbox.", RESOLVED_AT);

        assertEquals(CaseStatus.WAITING_FOR_CUSTOMER, supportCase.getStatus());
        assertEquals(2, supportCase.getMessages().size());
        assertEquals(MessageAuthorType.SUPPORT_AGENT, supportCase.getMessages().getLast().authorType());
        assertEquals(RESOLVED_AT, supportCase.getUpdatedAt());
    }

    @Test
    void requesterReplyMovesWaitingCaseBackToInProgress() {
        SupportCase supportCase = openCase();
        supportCase.assignTo(20L, REPLIED_AT);
        supportCase.reply(20L, MessageAuthorType.SUPPORT_AGENT, "Can you confirm your email?", REPLIED_AT);

        supportCase.reply(10L, MessageAuthorType.REQUESTER, "Yes, that is the correct email.", RESOLVED_AT);

        assertEquals(CaseStatus.IN_PROGRESS, supportCase.getStatus());
        assertEquals(3, supportCase.getMessages().size());
        assertEquals(MessageAuthorType.REQUESTER, supportCase.getMessages().getLast().authorType());
    }

    @Test
    void requesterCannotWriteAMessageAsAnotherRequester() {
        SupportCase supportCase = openCase();

        assertThrows(InvalidSupportCaseStateException.class, () ->
            supportCase.reply(99L, MessageAuthorType.REQUESTER, "A reply", REPLIED_AT)
        );
        assertEquals(1, supportCase.getMessages().size());
    }

    @Test
    void resolvingCaseSetsResolvedAtAndPreventsFurtherChanges() {
        SupportCase supportCase = openCase();

        supportCase.resolve(RESOLVED_AT);

        assertEquals(CaseStatus.RESOLVED, supportCase.getStatus());
        assertEquals(RESOLVED_AT, supportCase.getResolvedAt());
        assertThrows(InvalidSupportCaseStateException.class, () -> supportCase.assignTo(20L, RESOLVED_AT));
        assertThrows(InvalidSupportCaseStateException.class, () ->
            supportCase.reply(10L, MessageAuthorType.REQUESTER, "One more thing", RESOLVED_AT)
        );
        assertThrows(InvalidSupportCaseStateException.class, () -> supportCase.resolve(RESOLVED_AT));
    }

    @Test
    void messagesCannotBeMutatedOutsideTheAggregate() {
        SupportCase supportCase = openCase();

        assertThrows(UnsupportedOperationException.class, () -> supportCase.getMessages().clear());
    }


    @Test
    void invalidOperationTimestampsDoNotPartiallyMutateTheAggregate() {
        SupportCase supportCase = openCase();

        assertThrows(NullPointerException.class, () -> supportCase.assignTo(20L, null));
        assertNull(supportCase.getAssignedSupportUserId());
        assertEquals(CaseStatus.OPEN, supportCase.getStatus());

        assertThrows(IllegalArgumentException.class, () -> supportCase.assignTo(20L, OPENED_AT.minusSeconds(1)));
        assertNull(supportCase.getAssignedSupportUserId());
        assertEquals(CaseStatus.OPEN, supportCase.getStatus());

        assertThrows(NullPointerException.class, () -> supportCase.resolve(null));
        assertEquals(CaseStatus.OPEN, supportCase.getStatus());
        assertNull(supportCase.getResolvedAt());
    }

    @Test
    void operationTimestampsCannotMoveBackwards() {
        SupportCase supportCase = openCase();
        supportCase.assignTo(20L, REPLIED_AT);

        assertThrows(IllegalArgumentException.class, () ->
            supportCase.reply(10L, MessageAuthorType.REQUESTER, "A reply", OPENED_AT)
        );
        assertEquals(1, supportCase.getMessages().size());
        assertEquals(REPLIED_AT, supportCase.getUpdatedAt());
    }

    @Test
    void contextualIdentifiersAreStoredWithoutImportingOtherDomainModels() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        SupportCase supportCase = SupportCase.open(
            10L, "Payment question", IssueCategory.PAYMENT, "I have a question.", eventId, orderId, ticketId, OPENED_AT
        );

        assertEquals(eventId, supportCase.getEventId());
        assertEquals(orderId, supportCase.getOrderId());
        assertEquals(ticketId, supportCase.getTicketId());
    }
}
