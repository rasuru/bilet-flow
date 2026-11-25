package com.biletflow.biletflow.ordercheckout.rest;

import com.biletflow.biletflow.ordercheckout.application.AdminOrderOperations;
import com.biletflow.biletflow.ordercheckout.application.OrderAdminQueries;
import com.biletflow.biletflow.ordercheckout.application.OrderAdministrationQueryService;
import com.biletflow.biletflow.ordercheckout.domain.order.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Order administration")
@ApiResponse(responseCode = "400", description = "Invalid request")
@ApiResponse(responseCode = "401", description = "Authentication required")
@ApiResponse(responseCode = "403", description = "Administrator access required")
public class OrderAdministrationResource {

    private final OrderAdministrationQueryService orders;

    private final AdminOrderOperations operations;

    public OrderAdministrationResource(OrderAdministrationQueryService orders, AdminOrderOperations operations) {
        this.orders = orders;
        this.operations = operations;
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Read issued tickets, payment, refund, and available actions")
    public ResponseEntity<AdminOrderOperations.Detail> detail(@PathVariable UUID orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(operations.get(orderId));
    }

    public record OrderAction(@NotNull AdminOrderOperations.Action action, UUID ticketId, @Size(max = 200) String reason) {}

    @PostMapping("/orders/{orderId}/actions")
    @Operation(summary = "Resend a ticket, cancel a free order, or request a full refund")
    public ResponseEntity<AdminOrderOperations.Detail> execute(@PathVariable UUID orderId, @Valid @RequestBody OrderAction command) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(operations.execute(orderId, command.action(), command.ticketId(), command.reason()));
    }

    @GetMapping("/orders")
    @Operation(
        summary = "Search issued orders",
        description = "Results are ordered by order ID. This endpoint reads orders, excluding checkout attempts that never issued an order."
    )
    public ResponseEntity<List<OrderAdminQueries.View>> orders(
        @RequestParam(required = false) UUID orderId,
        @RequestParam(required = false) UUID eventId,
        @RequestParam(required = false) Long ownerId,
        @RequestParam(required = false) OrderStatus status,
        @RequestParam(defaultValue = "0") @Min(0) int offset,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        var filter = new OrderAdminQueries.Filter(orderId, eventId, ownerId, status, null, false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orders.search(filter, offset, limit));
    }

    @GetMapping("/promotion-activity")
    @Operation(
        summary = "Review orders attributed to promotions",
        description = "Lists issued orders with a recorded promotion ID and discount. Results are ordered by order ID."
    )
    public ResponseEntity<List<OrderAdminQueries.View>> promotionActivity(
        @RequestParam(required = false) UUID eventId,
        @RequestParam(required = false) UUID promotionId,
        @RequestParam(required = false) OrderStatus status,
        @RequestParam(defaultValue = "0") @Min(0) int offset,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        var filter = new OrderAdminQueries.Filter(null, eventId, null, status, promotionId, true);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orders.search(filter, offset, limit));
    }
}
