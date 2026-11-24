package com.biletflow.biletflow.iam.application.organizer;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrganizerProfileService {
    private final CurrentActor actor;
    private final OrganizerProfileRepository profiles;

    public OrganizerProfileService(CurrentActor actor, OrganizerProfileRepository profiles) {
        this.actor = Objects.requireNonNull(actor);
        this.profiles = Objects.requireNonNull(profiles);
    }

    public OrganizerProfileView getMine() {
        Long id = actor.requireUserId();
        return profiles.findByOrganizerId(id).orElseGet(() -> new OrganizerProfileView(id, null, null, null, null, null, null, null, null));
    }

    public OrganizerProfilePublicView getPublic(Long organizerId) {
        if (!profiles.organizerExists(Objects.requireNonNull(organizerId))) {
            throw new EntityNotFoundException("Organizer account not found: " + organizerId);
        }
        OrganizerProfileView profile = profiles.findByOrganizerId(organizerId)
            .orElseThrow(() -> new EntityNotFoundException("Organizer profile not found: " + organizerId));
        return new OrganizerProfilePublicView(profile.organizerId(), profile.organizationName(), profile.website(),
            profile.address(), profile.description());
    }

    @Transactional
    public OrganizerProfileView update(UpdateOrganizerProfileCommand command) {
        Objects.requireNonNull(command);
        Long organizerId = actor.requireUserId();
        if (!profiles.organizerExists(organizerId)) throw new EntityNotFoundException("Organizer account not found: " + organizerId);
        String name = command.organizationName() == null ? null : command.organizationName().trim();
        if (name == null || name.isBlank()) throw new IllegalArgumentException("organizationName is required");
        return profiles.save(organizerId, command);
    }
}
