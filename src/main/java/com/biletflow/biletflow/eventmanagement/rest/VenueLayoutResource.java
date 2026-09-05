package com.biletflow.biletflow.eventmanagement.rest;

import com.biletflow.biletflow.eventmanagement.application.venuelayout.VenueLayoutService;
import com.biletflow.biletflow.eventmanagement.application.venuelayout.view.VenueLayoutView;
import com.biletflow.biletflow.eventmanagement.domain.VenueLayoutId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/venue-layouts")
public class VenueLayoutResource {

    private final VenueLayoutService venueLayoutService;

    public VenueLayoutResource(VenueLayoutService venueLayoutService) {
        this.venueLayoutService = Objects.requireNonNull(venueLayoutService, "venueLayoutService cannot be null");
    }

    @GetMapping
    public List<VenueLayoutView> list() {
        return venueLayoutService.list();
    }

    @GetMapping("/{layoutId}")
    public VenueLayoutView get(@PathVariable UUID layoutId) {
        return venueLayoutService.get(new VenueLayoutId(layoutId));
    }
}
