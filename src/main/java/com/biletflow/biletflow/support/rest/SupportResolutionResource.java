package com.biletflow.biletflow.support.rest;

import com.biletflow.biletflow.support.application.SupportResolutionService;
import com.biletflow.biletflow.support.application.SupportResolutionService.Action;
import com.biletflow.biletflow.support.application.SupportResolutionService.Context;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support/cases/{caseId}")
public class SupportResolutionResource {
    private final SupportResolutionService resolution;

    public SupportResolutionResource(SupportResolutionService resolution) {
        this.resolution = resolution;
    }

    public record ActionRequest(@NotNull Action action, UUID ticketId, @Size(max = 200) String reason) {}

    @GetMapping("/context")
    public ResponseEntity<Context> context(@PathVariable UUID caseId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resolution.context(caseId));
    }

    @PostMapping("/actions")
    public ResponseEntity<Context> action(@PathVariable UUID caseId, @Valid @RequestBody ActionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(resolution.execute(caseId, request.action(), request.ticketId(), request.reason()));
    }
}

