package com.biletflow.biletflow.ticketinventory.rest;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ticketinventory.application.tickettype.TicketTypeService;
import com.biletflow.biletflow.ticketinventory.application.tickettype.command.CreateAssignedSeatingTicketTypeCommand;
import com.biletflow.biletflow.ticketinventory.application.tickettype.command.CreateGeneralAdmissionTicketTypeCommand;
import com.biletflow.biletflow.ticketinventory.application.tickettype.command.HideTicketTypeCommand;
import com.biletflow.biletflow.ticketinventory.application.tickettype.command.UnhideTicketTypeCommand;
import com.biletflow.biletflow.ticketinventory.application.tickettype.command.UpdateAssignedSeatingTicketTypeCommand;
import com.biletflow.biletflow.ticketinventory.application.tickettype.command.UpdateGeneralAdmissionTicketTypeCommand;
import com.biletflow.biletflow.ticketinventory.application.tickettype.view.TicketTypeView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TicketTypeResource {

    private final TicketTypeService ticketTypeService;

    public TicketTypeResource(TicketTypeService ticketTypeService) {
        this.ticketTypeService = Objects.requireNonNull(ticketTypeService, "ticketTypeService cannot be null");
    }

    @GetMapping("/events/{eventId}/ticket-types")
    public List<TicketTypeView> listPublicForEvent(@PathVariable UUID eventId) {
        return ticketTypeService.listPublicForEvent(eventId);
    }

    @GetMapping("/events/{eventId}/ticket-types/manage")
    public List<TicketTypeView> listForManagement(@PathVariable UUID eventId) {
        return ticketTypeService.listForManagement(eventId);
    }

    @PostMapping("/events/{eventId}/ticket-types/general-admission")
    public ResponseEntity<IdResponse> createGeneralAdmission(
        @PathVariable UUID eventId,
        @Valid @RequestBody GeneralAdmissionTicketTypeRequest request
    ) {
        UUID id = ticketTypeService.create(
            new CreateGeneralAdmissionTicketTypeCommand(
                eventId,
                request.name(),
                request.description(),
                Money.kzt(request.price()),
                request.capacity(),
                request.maxPerOrder(),
                request.salesStartAt(),
                request.salesEndAt()
            )
        );

        return ResponseEntity.created(URI.create("/api/events/" + eventId + "/ticket-types/manage")).body(new IdResponse(id));
    }

    @PostMapping("/events/{eventId}/ticket-types/assigned-seating")
    public ResponseEntity<IdResponse> createAssignedSeating(
        @PathVariable UUID eventId,
        @Valid @RequestBody AssignedSeatingTicketTypeRequest request
    ) {
        UUID id = ticketTypeService.create(
            new CreateAssignedSeatingTicketTypeCommand(
                eventId,
                request.name(),
                request.description(),
                Money.kzt(request.price()),
                request.priceCategory(),
                request.maxPerOrder(),
                request.salesStartAt(),
                request.salesEndAt()
            )
        );

        return ResponseEntity.created(URI.create("/api/events/" + eventId + "/ticket-types/manage")).body(new IdResponse(id));
    }

    @PutMapping("/ticket-types/{ticketTypeId}/general-admission")
    public ResponseEntity<Void> updateGeneralAdmission(
        @PathVariable UUID ticketTypeId,
        @Valid @RequestBody GeneralAdmissionTicketTypeRequest request
    ) {
        ticketTypeService.update(
            new UpdateGeneralAdmissionTicketTypeCommand(
                ticketTypeId,
                request.name(),
                request.description(),
                Money.kzt(request.price()),
                request.capacity(),
                request.maxPerOrder(),
                request.salesStartAt(),
                request.salesEndAt()
            )
        );

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/ticket-types/{ticketTypeId}/assigned-seating")
    public ResponseEntity<Void> updateAssignedSeating(
        @PathVariable UUID ticketTypeId,
        @Valid @RequestBody AssignedSeatingTicketTypeRequest request
    ) {
        ticketTypeService.update(
            new UpdateAssignedSeatingTicketTypeCommand(
                ticketTypeId,
                request.name(),
                request.description(),
                Money.kzt(request.price()),
                request.maxPerOrder(),
                request.salesStartAt(),
                request.salesEndAt()
            )
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ticket-types/{ticketTypeId}/hide")
    public ResponseEntity<Void> hide(@PathVariable UUID ticketTypeId) {
        ticketTypeService.hide(new HideTicketTypeCommand(ticketTypeId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ticket-types/{ticketTypeId}/unhide")
    public ResponseEntity<Void> unhide(@PathVariable UUID ticketTypeId) {
        ticketTypeService.unhide(new UnhideTicketTypeCommand(ticketTypeId));
        return ResponseEntity.noContent().build();
    }

    public record IdResponse(UUID id) {
        public IdResponse {
            Objects.requireNonNull(id, "id cannot be null");
        }
    }

    public record GeneralAdmissionTicketTypeRequest(
        @NotBlank String name,
        @NotNull String description,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal price,
        @Positive int capacity,
        @Positive int maxPerOrder,
        @NotNull Instant salesStartAt,
        @NotNull Instant salesEndAt
    ) {}

    public record AssignedSeatingTicketTypeRequest(
        @NotBlank String name,
        @NotNull String description,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal price,
        @NotBlank String priceCategory,
        @Positive int maxPerOrder,
        @NotNull Instant salesStartAt,
        @NotNull Instant salesEndAt
    ) {}
}
