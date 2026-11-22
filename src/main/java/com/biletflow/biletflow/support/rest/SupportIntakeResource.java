package com.biletflow.biletflow.support.rest;

import com.biletflow.biletflow.support.application.SupportAdministrationService;
import com.biletflow.biletflow.support.domain.supportcase.IssueCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support/cases")
public class SupportIntakeResource {

    private final SupportAdministrationService support;

    public SupportIntakeResource(SupportAdministrationService support) {
        this.support = support;
    }

    public record OpenRequest(
        @NotBlank @Size(max = 255) String subject,
        @NotNull IssueCategory category,
        @NotBlank @Size(max = 4000) String message,
        UUID eventId
    ) {}

    public record CaseReceipt(UUID caseId) {}

    @PostMapping("/platform")
    @Operation(summary = "Open a platform support case", description = "Link an event only if you organize it.")
    @ApiResponse(responseCode = "201", description = "Case opened")
    @ApiResponse(responseCode = "403", description = "Caller does not organize the referenced event")
    public ResponseEntity<CaseReceipt> open(@Valid @RequestBody OpenRequest request) {
        UUID caseId = support.openPlatformCase(request.subject(), request.category(), request.message(), request.eventId());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(new CaseReceipt(caseId));
    }

    @PostMapping("/{caseId}/escalate")
    @Operation(
        summary = "Escalate an existing attendee support case to platform administrators",
        description = "Only the event organizer can escalate the case."
    )
    @ApiResponse(responseCode = "204", description = "Case escalated")
    @ApiResponse(responseCode = "404", description = "Case not found for this organizer")
    @ApiResponse(responseCode = "409", description = "Case is resolved or cannot be escalated")
    public ResponseEntity<Void> escalate(@PathVariable UUID caseId) {
        support.escalate(caseId);
        return ResponseEntity.noContent().build();
    }
}
