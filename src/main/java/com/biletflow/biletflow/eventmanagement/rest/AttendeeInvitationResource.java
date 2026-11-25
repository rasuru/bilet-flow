package com.biletflow.biletflow.eventmanagement.rest;

import com.biletflow.biletflow.common.rest.ApiProblems;
import com.biletflow.biletflow.eventmanagement.application.socialevent.PrivateAttendeeAccess;
import com.biletflow.biletflow.eventmanagement.application.socialevent.PrivateAttendeeAccess.InvitationView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/attendee-invitations")
public class AttendeeInvitationResource {

    private final PrivateAttendeeAccess access;

    public AttendeeInvitationResource(PrivateAttendeeAccess access) {
        this.access = access;
    }

    @GetMapping
    public ResponseEntity<List<InvitationView>> list(@PathVariable UUID eventId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(access.list(eventId));
    }

    @PostMapping
    public ResponseEntity<Void> invite(@PathVariable UUID eventId, @Valid @RequestBody InviteRequest request) {
        access.invite(eventId, request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{invitationId}/resend")
    public ResponseEntity<Void> resend(@PathVariable UUID eventId, @PathVariable UUID invitationId) {
        access.resend(eventId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{invitationId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID eventId, @PathVariable UUID invitationId) {
        access.revoke(eventId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accept")
    public ResponseEntity<Void> accept(@PathVariable UUID eventId, @Valid @RequestBody AcceptRequest request) {
        access.accept(eventId, request.token());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(PrivateAttendeeAccess.InvitationDeliveryException.class)
    public ResponseEntity<ProblemDetail> emailRejected() {
        return ApiProblems.response(
            ApiProblems.body(
                HttpStatus.SERVICE_UNAVAILABLE,
                "invitation-email-unavailable",
                "Invitation email unavailable",
                "The invitation was saved. Try resending the email."
            )
        );
    }

    public record InviteRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record AcceptRequest(@NotNull UUID token) {}
}
