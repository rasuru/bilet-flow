package com.biletflow.biletflow.eventmanagement.rest;

import com.biletflow.biletflow.eventmanagement.application.socialevent.EventReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/reports")
public class EventReportResource {

    private final EventReportService reports;

    public EventReportResource(EventReportService reports) {
        this.reports = reports;
    }

    public record ReportRequest(@NotBlank @Size(max = 1000) String reason) {}
    public record ReportReceipt(UUID reportId) {}

    @PostMapping
    @Operation(
        summary = "Report an event visible to the caller",
        description = "Repeat submissions for the same event and reporter return the original report ID."
    )
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "404", description = "Event is unavailable to the reporter")
    public ResponseEntity<ReportReceipt> report(@PathVariable UUID eventId, @Valid @RequestBody ReportRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ReportReceipt(reports.submit(eventId, request.reason())));
    }
}
