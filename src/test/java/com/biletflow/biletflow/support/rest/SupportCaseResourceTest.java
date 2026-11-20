package com.biletflow.biletflow.support.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.support.application.SupportCaseApplicationService;
import com.biletflow.biletflow.support.application.command.*;
import com.biletflow.biletflow.support.application.view.SupportCaseView;
import com.biletflow.biletflow.support.domain.supportcase.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SupportCaseResourceTest {
    private SupportCaseApplicationService application;
    private SupportCaseResource resource;
    private final Instant now = Instant.parse("2026-11-20T10:00:00Z");

    @BeforeEach
    void setUp() {
        application = mock(SupportCaseApplicationService.class);
        resource = new SupportCaseResource(application);
    }

    private SupportCaseView view(UUID id) {
        return new SupportCaseView(id, SupportCaseType.ATTENDEE_SUPPORT, 10L, "Subject",
            IssueCategory.OTHER, null, null, null, CaseStatus.OPEN, null, null,
            now, now, null, List.of());
    }

    @Test
    void openReturnsCreatedAndLocation() {
        UUID id = UUID.randomUUID();
        when(application.open(any())).thenReturn(view(id));
        var response = resource.open(new SupportCaseResource.OpenCaseRequest(
            SupportCaseType.ATTENDEE_SUPPORT, "Subject", IssueCategory.OTHER,
            "Initial message", null, null, null));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/support/cases/" + id, response.getHeaders().getLocation().toString());
        verify(application).open(any(OpenSupportCaseCommand.class));
    }

    @Test
    void listDelegatesToApplication() {
        when(application.list()).thenReturn(List.of(view(UUID.randomUUID())));

        assertEquals(1, resource.list().size());
        verify(application).list();
    }

    @Test
    void getDelegatesToApplication() {
        UUID id = UUID.randomUUID();
        when(application.get(id)).thenReturn(view(id));

        assertEquals(id, resource.get(id).id());
    }

    @Test
    void replyBuildsApplicationCommand() {
        UUID id = UUID.randomUUID();
        when(application.reply(any())).thenReturn(view(id));

        resource.reply(id, new SupportCaseResource.ReplyRequest("Additional details"));

        verify(application).reply(new ReplyToSupportCaseCommand(id, "Additional details"));
    }

    @Test
    void statusChangeBuildsApplicationCommand() {
        UUID id = UUID.randomUUID();
        when(application.changeStatus(any())).thenReturn(view(id));

        resource.changeStatus(id, new SupportCaseResource.ChangeStatusRequest(CaseStatus.IN_PROGRESS));

        verify(application).changeStatus(new ChangeSupportCaseStatusCommand(id, CaseStatus.IN_PROGRESS));
    }

    @Test
    void assignmentBuildsApplicationCommand() {
        UUID id = UUID.randomUUID();
        when(application.assign(any())).thenReturn(view(id));

        resource.assign(id, new SupportCaseResource.AssignRequest(20L));

        verify(application).assign(new AssignSupportCaseCommand(id, 20L));
    }
}
