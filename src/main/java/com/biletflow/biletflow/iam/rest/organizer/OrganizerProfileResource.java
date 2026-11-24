package com.biletflow.biletflow.iam.rest.organizer;

import com.biletflow.biletflow.iam.application.organizer.OrganizerProfileService;
import com.biletflow.biletflow.iam.application.organizer.OrganizerProfileView;
import com.biletflow.biletflow.iam.application.organizer.OrganizerProfilePublicView;
import com.biletflow.biletflow.iam.application.organizer.UpdateOrganizerProfileCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organizer-profile")
public class OrganizerProfileResource {

    private final OrganizerProfileService profiles;

    public OrganizerProfileResource(OrganizerProfileService profiles) {
        this.profiles = Objects.requireNonNull(profiles);
    }

    public record UpdateRequest(
        @NotBlank @Size(max = 120) String organizationName,
        @Size(max = 120) String contactName,
        @Email @Size(max = 254) String contactEmail,
        @Pattern(regexp = "^$|^[+0-9() .-]{7,30}$") String contactPhone,
        @Pattern(regexp = "^$|^https?://.{3,500}$") String website,
        @Size(max = 512) String address,
        @Size(max = 2000) String description
    ) {
        UpdateOrganizerProfileCommand toCommand() {
            return new UpdateOrganizerProfileCommand(organizationName, contactName, contactEmail, contactPhone, website, address, description);
        }
    }

    @GetMapping("/me")
    public OrganizerProfileView getMine() {
        return profiles.getMine();
    }

    @PutMapping("/me")
    public OrganizerProfileView update(@Valid @RequestBody UpdateRequest request) {
        return profiles.update(request.toCommand());
    }

    @GetMapping("/public/{organizerId}")
    public OrganizerProfilePublicView getPublic(@PathVariable Long organizerId) {
        return profiles.getPublic(organizerId);
    }
}
