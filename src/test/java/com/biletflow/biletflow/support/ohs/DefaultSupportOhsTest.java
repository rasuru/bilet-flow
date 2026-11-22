package com.biletflow.biletflow.support.ohs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.support.application.SupportCaseApplicationService;
import com.biletflow.biletflow.support.application.command.OpenSupportCaseCommand;
import com.biletflow.biletflow.support.application.command.ReplyToSupportCaseCommand;
import com.biletflow.biletflow.support.application.view.SupportCaseView;
import com.biletflow.biletflow.support.domain.supportcase.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultSupportOhsTest {
    private SupportCaseApplicationService application;
    private DefaultSupportOhs ohs;

    @BeforeEach
    void setUp() {
        application = mock(SupportCaseApplicationService.class);
        ohs = new DefaultSupportOhs(application);
    }

    private SupportCaseView view(UUID id) {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        return new SupportCaseView(id, SupportCaseType.ATTENDEE_SUPPORT, 10L,
            "Subject", IssueCategory.OTHER, null, null, null, CaseStatus.OPEN,
            null, null, now, now, null, null, List.of());
    }

    @Test
    void getCaseMapsApplicationViewToPublishedSummary() {
        UUID id = UUID.randomUUID();
        when(application.get(id)).thenReturn(view(id));

        assertEquals(id, ohs.getCase(id).caseId());
        verify(application).get(id);
    }

    @Test
    void myCasesMapsEveryResult() {
        UUID id = UUID.randomUUID();
        when(application.list()).thenReturn(List.of(view(id)));

        assertEquals(1, ohs.myCases().size());
        assertEquals(id, ohs.myCases().getFirst().caseId());
    }

    @Test
    void openCaseDelegatesToApplicationService() {
        var command = new OpenSupportCaseCommand(SupportCaseType.ATTENDEE_SUPPORT,
            "Subject", IssueCategory.OTHER, "Message", null, null, null);
        UUID id = UUID.randomUUID();
        when(application.open(command)).thenReturn(view(id));

        assertEquals(id, ohs.openCase(command).caseId());
    }

    @Test
    void addMessageDelegatesUsingCaseIdAndContent() {
        UUID id = UUID.randomUUID();
        when(application.reply(new ReplyToSupportCaseCommand(id, "Hello"))).thenReturn(view(id));

        assertEquals(id, ohs.addMessage(id, "Hello").caseId());
    }
}
