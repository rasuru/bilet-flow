package com.biletflow.biletflow.ticketinventory.rest;

import com.biletflow.biletflow.ticketinventory.application.delivery.TicketDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Ticket delivery", description = "Private ticket documents and delivery to the ticket's recorded attendee email.")
@ApiResponses({
    @ApiResponse(
        responseCode = "401",
        description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "403",
        description = "Caller is not the ticket owner, verified recipient, or event organizer",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "404",
        description = "Ticket not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "409",
        description = "Operation is unavailable for this ticket or email resend cooldown is active",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
})
public class TicketDocumentsResource {

    private final TicketDocumentService documents;

    public TicketDocumentsResource(TicketDocumentService documents) {
        this.documents = Objects.requireNonNull(documents);
    }

    @GetMapping(value = "/{ticketId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(
        summary = "Download a printable ticket",
        description = "A4 PDF with event details, attendee email, seat, status, and the canonical admission QR. Cancelled/refunded/pending-refund tickets have no admission QR."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Printable ticket",
        content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE, schema = @Schema(type = "string", format = "binary"))
    )
    public ResponseEntity<byte[]> pdf(@PathVariable UUID ticketId) {
        return file(documents.pdf(ticketId), MediaType.APPLICATION_PDF, "ticket-" + ticketId + ".pdf");
    }

    @GetMapping(value = "/{ticketId}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(
        summary = "Download the admission QR image",
        description = "PNG encoding the existing ticket code, accepted by the online scanner. Printed and digital copies do not create additional admissions."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Ticket QR image",
        content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary"))
    )
    public ResponseEntity<byte[]> qr(@PathVariable UUID ticketId) {
        return file(documents.qr(ticketId), MediaType.IMAGE_PNG, "ticket-" + ticketId + ".png");
    }

    @PostMapping("/{ticketId}/delivery")
    @Operation(
        summary = "Request ticket email delivery",
        description = "Always sends to the recorded attendee email; no recipient override. Pending requests are idempotent. Completed deliveries can be resent after a one-minute cooldown. SMTP delivery is asynchronous and retried on failure."
    )
    @ApiResponse(responseCode = "202", description = "Delivery queued or already pending", content = @Content)
    public ResponseEntity<Void> email(@PathVariable UUID ticketId) {
        documents.requestEmail(ticketId);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<byte[]> file(byte[] content, MediaType type, String filename) {
        return ResponseEntity.ok()
            .contentType(type)
            .contentLength(content.length)
            .cacheControl(CacheControl.noStore().cachePrivate())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
            .body(content);
    }
}
