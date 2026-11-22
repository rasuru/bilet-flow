package com.biletflow.biletflow.support.rest;

import com.biletflow.biletflow.support.application.SupportCaseApplicationService;
import com.biletflow.biletflow.support.application.command.AssignSupportCaseCommand;
import com.biletflow.biletflow.support.application.command.ChangeSupportCaseStatusCommand;
import com.biletflow.biletflow.support.application.command.OpenSupportCaseCommand;
import com.biletflow.biletflow.support.application.command.ReplyToSupportCaseCommand;
import com.biletflow.biletflow.support.application.view.SupportCaseView;
import com.biletflow.biletflow.support.domain.supportcase.CaseStatus;
import com.biletflow.biletflow.support.domain.supportcase.IssueCategory;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support/cases")
@Tag(name = "Support cases", description = "Attendee support requests and support-agent case management.")
public class SupportCaseResource {

    private final SupportCaseApplicationService supportCases;

    public SupportCaseResource(SupportCaseApplicationService supportCases) {
        this.supportCases = Objects.requireNonNull(supportCases, "supportCases cannot be null");
    }

    public record OpenCaseRequest(
        @NotNull SupportCaseType type,
        @NotBlank @Size(max = 255) String subject,
        @NotNull IssueCategory category,
        @NotBlank String initialMessage,
        UUID eventId,
        UUID orderId,
        UUID ticketId
    ) {}

    public record ReplyRequest(@NotBlank String content) {}

    public record ChangeStatusRequest(@NotNull CaseStatus status) {}

    public record AssignRequest(@NotNull @Positive Long supportUserId) {}

    @PostMapping
    @Operation(summary = "Open a support case")
    public ResponseEntity<SupportCaseView> open(@Valid @RequestBody OpenCaseRequest request) {
        SupportCaseView created = supportCases.open(new OpenSupportCaseCommand(
            request.type(),
            request.subject(),
            request.category(),
            request.initialMessage(),
            request.eventId(),
            request.orderId(),
            request.ticketId()
        ));

        return ResponseEntity.created(URI.create("/api/support/cases/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "List support cases visible to the current user")
    public List<SupportCaseView> list() {
        return supportCases.list();
    }

    @GetMapping("/{caseId}")
    @Operation(summary = "Get a support case and its message thread")
    public SupportCaseView get(@PathVariable UUID caseId) {
        return supportCases.get(caseId);
    }

    @PostMapping("/{caseId}/messages")
    @Operation(summary = "Reply to a support case")
    public SupportCaseView reply(@PathVariable UUID caseId, @Valid @RequestBody ReplyRequest request) {
        return supportCases.reply(new ReplyToSupportCaseCommand(caseId, request.content()));
    }

    @PatchMapping("/{caseId}/status")
    @Operation(summary = "Change a support case status (authorized support staff)")
    public SupportCaseView changeStatus(
        @PathVariable UUID caseId,
        @Valid @RequestBody ChangeStatusRequest request
    ) {
        return supportCases.changeStatus(new ChangeSupportCaseStatusCommand(caseId, request.status()));
    }

    @PatchMapping("/{caseId}/assignment")
    @Operation(summary = "Assign a support case to an authorized support agent")
    public SupportCaseView assign(@PathVariable UUID caseId, @Valid @RequestBody AssignRequest request) {
        return supportCases.assign(new AssignSupportCaseCommand(caseId, request.supportUserId()));
    }
}
