package com.biletflow.biletflow.support.rest;

import com.biletflow.biletflow.support.application.SupportAdminQueries;
import com.biletflow.biletflow.support.application.SupportAdministrationService;
import com.biletflow.biletflow.support.domain.supportcase.CaseStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/support-cases")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Support administration")
@ApiResponse(responseCode = "400", description = "Invalid request")
@ApiResponse(responseCode = "401", description = "Authentication required")
@ApiResponse(responseCode = "403", description = "Administrator access required")
public class SupportAdministrationResource {

    private final SupportAdministrationService support;

    public SupportAdministrationResource(SupportAdministrationService support) {
        this.support = support;
    }

    @GetMapping
    @Operation(
        summary = "List platform support cases and escalated attendee cases",
        description = "Use escalatedOnly=true to list attendee escalations only. Most recently updated first."
    )
    public ResponseEntity<List<SupportAdminQueries.Summary>> cases(
        @RequestParam(required = false) CaseStatus status,
        @RequestParam(required = false) UUID eventId,
        @RequestParam(defaultValue = "false") boolean escalatedOnly,
        @RequestParam(defaultValue = "0") @Min(0) int offset,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.search(status, eventId, escalatedOnly, offset, limit));
    }

    @GetMapping("/{caseId}")
    @Operation(summary = "Read a platform support case or escalated attendee case and its message thread")
    @ApiResponse(responseCode = "404", description = "Case absent from the platform support queue")
    public ResponseEntity<SupportAdminQueries.Detail> detail(@PathVariable UUID caseId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.get(caseId));
    }
}
