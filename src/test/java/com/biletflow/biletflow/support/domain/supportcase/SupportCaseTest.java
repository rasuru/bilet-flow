package com.biletflow.biletflow.support.domain.supportcase;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.support.domain.exceptions.InvalidSupportCaseStateException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupportCaseTest {

    private static final Instant OPENED_AT = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant REPLIED_AT = OPENED_AT.plusSeconds(60);
    private static final Instant STATUS_CHANGED_AT = OPENED_AT.plusSeconds(120);
    private static final Instant RESOLVED_AT = OPENED_AT.plusSeconds(180);

    private SupportCase openCase() {
        return SupportCase.open(
            SupportCaseType.ATTENDEE_SUPPORT,
            10L,
            "Ticket was not delivered",
            IssueCategory.TICKET_DELIVERY,
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
        assertEquals(SupportCaseType.ATTENDEE_SUPPORT, supportCase.getType());
        assertEquals(10L, supportCase.getRequesterUserId());
        assertEquals("Ticket was not delivered", supportCase.getSubject());
        assertEquals(IssueCategory.TICKET_DELIVERY, supportCase.getCategory());
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
            SupportCase.open(null, 10L, "Subject", IssueCategory.OTHER, "Message", UUID.randomUUID(), null, null, OPENED_AT)
        );
        assertThrows(NullPointerException.class, () ->
            SupportCase.open(SupportCaseType.ATTENDEE_SUPPORT, null, "Subject", IssueCategory.OTHER, "Message", UUID.randomUUID(), null, null, OPENED_AT)
        );
        assertThrows(IllegalArgumentException.class, () ->
            SupportCase.open(SupportCaseType.ATTENDEE_SUPPORT, 10L, "  ", IssueCategory.OTHER, "Message", UUID.randomUUID(), null, null, OPENED_AT)
        );
        assertThrows(IllegalArgumentException.class, () ->
            SupportCase.open(SupportCaseType.ATTENDEE_SUPPORT, 10L, "Subject", IssueCategory.OTHER, "  ", UUID.randomUUID(), null, null, OPENED_AT)
        );
    }

    @Test
    void attendeeSupportCaseRequiresAnEventReference() {
        assertThrows(IllegalArgumentException.class, () -> SupportCase.open(
            SupportCaseType.ATTENDEE_SUPPORT, 10L, "Subject", IssueCategory.OTHER,
            "Message", null, null, null, OPENED_AT));
    }

    @Test
    void assigningAnOpenCaseSetsAssigneeAndKeepsStatusOpen() {
        SupportCase supportCase = openCase();

        supportCase.assignTo(20L, REPLIED_AT);

        assertEquals(CaseStatus.OPEN, supportCase.getStatus());
        assertEquals(20L, supportCase.getAssignedSupportUserId());
        assertEquals(REPLIED_AT, supportCase.getAssignedAt());
        assertEquals(REPLIED_AT, supportCase.getUpdatedAt());
    }

    @Test
    void supportAgentReplyAddsMessageWithoutChangingStatus() {
        SupportCase supportCase = openCase();
        supportCase.assignTo(20L, REPLIED_AT);
        supportCase.changeStatus(CaseStatus.IN_PROGRESS, REPLIED_AT);

        supportCase.replyAsSupportAgent(20L, "Please check your email inbox.", STATUS_CHANGED_AT);

        assertEquals(CaseStatus.IN_PROGRESS, supportCase.getStatus());
        assertEquals(2, supportCase.getMessages().size());
        assertEquals(MessageAuthorType.SUPPORT_AGENT, supportCase.getMessages().getLast().authorType());
        assertEquals(STATUS_CHANGED_AT, supportCase.getUpdatedAt());
    }

    @Test
    void requesterReplyAddsMessageWithoutChangingStatus() {
        SupportCase supportCase = openCase();

        supportCase.replyAsRequester(10L, "Here is some additional information.", REPLIED_AT);

        assertEquals(CaseStatus.OPEN, supportCase.getStatus());
        assertEquals(2, supportCase.getMessages().size());
        assertEquals(MessageAuthorType.REQUESTER, supportCase.getMessages().getLast().authorType());
    }

    @Test
    void requesterCannotWriteAMessageAsAnotherRequester() {
        SupportCase supportCase = openCase();

        assertThrows(InvalidSupportCaseStateException.class, () ->
            supportCase.replyAsRequester(99L, "A reply", REPLIED_AT)
        );
        assertEquals(1, supportCase.getMessages().size());
    }

    @Test
    void changeStatusAllowsRequiredValidTransitions() {
        SupportCase openCase = openCase();
        openCase.changeStatus(CaseStatus.IN_PROGRESS, REPLIED_AT);
        assertEquals(CaseStatus.IN_PROGRESS, openCase.getStatus());

        openCase.changeStatus(CaseStatus.WAITING_FOR_CUSTOMER, STATUS_CHANGED_AT);
        assertEquals(CaseStatus.WAITING_FOR_CUSTOMER, openCase.getStatus());

        openCase.changeStatus(CaseStatus.IN_PROGRESS, RESOLVED_AT.minusSeconds(30));
        assertEquals(CaseStatus.IN_PROGRESS, openCase.getStatus());

        openCase.changeStatus(CaseStatus.RESOLVED, RESOLVED_AT);
        assertEquals(CaseStatus.RESOLVED, openCase.getStatus());
        assertEquals(RESOLVED_AT, openCase.getResolvedAt());
    }

    @Test
    void changeStatusRejectsInvalidTransitionsAndChangesAfterResolution() {
        SupportCase supportCase = openCase();
        supportCase.changeStatus(CaseStatus.IN_PROGRESS, REPLIED_AT);

        assertThrows(InvalidSupportCaseStateException.class, () ->
            supportCase.changeStatus(CaseStatus.OPEN, STATUS_CHANGED_AT)
        );
        assertThrows(InvalidSupportCaseStateException.class, () ->
            supportCase.changeStatus(CaseStatus.IN_PROGRESS, STATUS_CHANGED_AT)
        );

        supportCase.changeStatus(CaseStatus.RESOLVED, STATUS_CHANGED_AT);
        assertThrows(InvalidSupportCaseStateException.class, () ->
            supportCase.changeStatus(CaseStatus.IN_PROGRESS, RESOLVED_AT)
        );
        assertThrows(InvalidSupportCaseStateException.class, () -> supportCase.assignTo(20L, RESOLVED_AT));
        assertThrows(InvalidSupportCaseStateException.class, () ->
            supportCase.replyAsRequester(10L, "One more thing", RESOLVED_AT)
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

        assertThrows(NullPointerException.class, () -> supportCase.changeStatus(CaseStatus.IN_PROGRESS, null));
        assertEquals(CaseStatus.OPEN, supportCase.getStatus());
    }

    @Test
    void operationTimestampsCannotMoveBackwards() {
        SupportCase supportCase = openCase();
        supportCase.assignTo(20L, REPLIED_AT);

        assertThrows(IllegalArgumentException.class, () ->
            supportCase.replyAsRequester(10L, "A reply", OPENED_AT)
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
            SupportCaseType.ATTENDEE_SUPPORT,
            10L,
            "Payment question",
            IssueCategory.PAYMENT,
            "I have a question.",
            eventId,
            orderId,
            ticketId,
            OPENED_AT
        );

        assertEquals(eventId, supportCase.getEventId());
        assertEquals(orderId, supportCase.getOrderId());
        assertEquals(ticketId, supportCase.getTicketId());
    }
}
