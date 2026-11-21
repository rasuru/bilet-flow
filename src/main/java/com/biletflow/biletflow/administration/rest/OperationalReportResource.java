package com.biletflow.biletflow.administration.rest;

import com.biletflow.biletflow.administration.application.OperationalReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Operational reporting")
@ApiResponse(responseCode = "401", description = "Authentication required")
@ApiResponse(responseCode = "403", description = "Administrator access required")
public class OperationalReportResource {

    private final OperationalReportService reports;

    public OperationalReportResource(OperationalReportService reports) {
        this.reports = reports;
    }

    @GetMapping(value = "/operations.csv", produces = "text/csv")
    @Operation(
        summary = "Export operational counts by resource and status",
        description = "Exports current counts platform-wide or for one event. The CSV contains resource, status and count columns."
    )
    @ApiResponse(
        responseCode = "200",
        description = "CSV download",
        content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))
    )
    public ResponseEntity<String> operations(@RequestParam(required = false) UUID eventId) {
        var csv = new StringBuilder("resource,status,count\r\n");
        for (var row : reports.countByStatus(eventId)) {
            csv.append(row.resource()).append(',').append(row.status()).append(',').append(row.count()).append("\r\n");
        }

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("operations.csv").build().toString())
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.toString());
    }
}
