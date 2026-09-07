package com.biletflow.biletflow.eventmanagement.rest;

import com.biletflow.biletflow.eventmanagement.application.venuelayout.VenueLayoutService;
import com.biletflow.biletflow.eventmanagement.application.venuelayout.view.VenueLayoutView;
import com.biletflow.biletflow.eventmanagement.domain.VenueLayoutId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/venue-layouts")
@Tag(name = "Venue layouts", description = "Read-only predefined assigned-seating layouts used when configuring an event venue.")
public class VenueLayoutResource {

    private final VenueLayoutService venueLayoutService;

    public VenueLayoutResource(VenueLayoutService venueLayoutService) {
        this.venueLayoutService = Objects.requireNonNull(venueLayoutService, "venueLayoutService cannot be null");
    }

    @GetMapping
    @Operation(
        summary = "List venue layouts",
        description = "Returns all predefined seating layouts, including their seats and seat metadata."
    )
    @ApiResponse(responseCode = "200", description = "Venue layouts returned")
    public List<VenueLayoutView> list() {
        return venueLayoutService.list();
    }

    @GetMapping("/{layoutId}")
    @Operation(summary = "Get a venue layout", description = "Returns one predefined assigned-seating layout and all of its seats.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Venue layout returned"),
        @ApiResponse(responseCode = "404", description = "Venue layout not found", content = @Content),
    })
    public VenueLayoutView get(@Parameter(description = "Venue layout ID", required = true) @PathVariable UUID layoutId) {
        return venueLayoutService.get(new VenueLayoutId(layoutId));
    }
}
