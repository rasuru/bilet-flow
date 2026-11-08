package com.biletflow.biletflow.support.persistence.supportcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.support.domain.supportcase.CaseStatus;
import com.biletflow.biletflow.support.domain.supportcase.IssueCategory;
import com.biletflow.biletflow.support.domain.supportcase.SupportCase;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseId;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseRepository;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class SupportCasePersistenceIT {

    @Autowired
    private SupportCaseRepository supportCases;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID caseId;

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanup() {
        if (caseId != null) {
            jdbc.update("delete from support_case where id = ?", caseId);
        }
    }

    @Test
    void savesAndRehydratesCaseWithItsOrderedMessageThread() {
        Instant openedAt = Instant.parse("2026-11-08T10:00:00Z");
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        SupportCase supportCase = SupportCase.open(
            SupportCaseType.ATTENDEE_SUPPORT,
            101L,
            "Ticket delivery issue",
            IssueCategory.TICKET,
            "I cannot find my ticket.",
            eventId,
            orderId,
            ticketId,
            openedAt
        );
        caseId = supportCase.getId().value();

        supportCase.assignTo(202L, openedAt.plusSeconds(10));
        supportCase.replyAsSupportAgent(202L, "I am checking the delivery status.", openedAt.plusSeconds(20));
        supportCase.changeStatus(CaseStatus.WAITING_FOR_CUSTOMER, openedAt.plusSeconds(30));

        tx().executeWithoutResult(status -> supportCases.save(supportCase));

        SupportCase reloaded = tx().execute(status -> supportCases.findById(new SupportCaseId(caseId)).orElseThrow());

        assertEquals(supportCase.getId(), reloaded.getId());
        assertEquals(SupportCaseType.ATTENDEE_SUPPORT, reloaded.getType());
        assertEquals(101L, reloaded.getRequesterUserId());
        assertEquals(202L, reloaded.getAssignedSupportUserId());
        assertEquals(CaseStatus.WAITING_FOR_CUSTOMER, reloaded.getStatus());
        assertEquals(eventId, reloaded.getEventId());
        assertEquals(orderId, reloaded.getOrderId());
        assertEquals(ticketId, reloaded.getTicketId());
        assertEquals(2, reloaded.getMessages().size());
        assertEquals("I cannot find my ticket.", reloaded.getMessages().getFirst().content());
        assertEquals("I am checking the delivery status.", reloaded.getMessages().getLast().content());
        assertEquals(openedAt.plusSeconds(20), reloaded.getMessages().getLast().sentAt());
        assertNull(reloaded.getResolvedAt());
    }

    @Test
    void supportsRequesterAssigneeAndStatusQueries() {
        Instant now = Instant.parse("2026-11-08T11:00:00Z");
        SupportCase supportCase = SupportCase.open(
            SupportCaseType.PLATFORM_SUPPORT,
            303L,
            "Organizer account issue",
            IssueCategory.ACCOUNT,
            "I need help with my organizer account.",
            null,
            null,
            null,
            now
        );
        caseId = supportCase.getId().value();
        supportCase.assignTo(404L, now.plusSeconds(1));
        supportCase.changeStatus(CaseStatus.IN_PROGRESS, now.plusSeconds(2));

        tx().executeWithoutResult(status -> supportCases.save(supportCase));

        assertEquals(1, tx().execute(status -> supportCases.findByRequesterUserId(303L).size()));
        assertEquals(1, tx().execute(status -> supportCases.findByAssignedSupportUserId(404L).size()));
        assertEquals(1L, tx().execute(status -> supportCases.findByStatus(CaseStatus.IN_PROGRESS).stream()
            .filter(candidate -> candidate.getId().equals(supportCase.getId()))
            .count()));
    }
}
