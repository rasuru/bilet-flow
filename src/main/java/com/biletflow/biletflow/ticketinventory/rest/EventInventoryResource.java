package com.biletflow.biletflow.ticketinventory.rest;

import com.biletflow.biletflow.ticketinventory.application.eventinventory.EventInventoryService;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.view.EventInventoryView;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.view.GaAvailabilityView;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.view.SeatAvailabilityView;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}")
public class EventInventoryResource {

    private final EventInventoryService inventoryService;

    public EventInventoryResource(EventInventoryService inventoryService) {
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService cannot be null");
    }

    @GetMapping("/inventory")
    public EventInventoryView getInventory(@PathVariable UUID eventId) {
        return inventoryService.getInventory(eventId);
    }

    @GetMapping("/availability")
    public List<GaAvailabilityView> getGeneralAdmissionAvailability(@PathVariable UUID eventId) {
        return inventoryService.getAvailability(eventId);
    }

    @GetMapping("/seat-map")
    public List<SeatAvailabilityView> getSeatAvailability(@PathVariable UUID eventId) {
        return inventoryService.getSeatMap(eventId);
    }
}
