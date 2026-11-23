package com.biletflow.biletflow.iam.application.organizer;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.iam.persistence.UserRepository;
import com.biletflow.biletflow.iam.persistence.organizer.OrganizerProfileJpaEntity;
import com.biletflow.biletflow.iam.persistence.organizer.OrganizerProfileJpaRepository;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrganizerProfileService {

    private final CurrentActor actor;
    private final UserRepository users;
    private final OrganizerProfileJpaRepository profiles;
    private final Clock clock;

    public OrganizerProfileService(CurrentActor actor, UserRepository users, OrganizerProfileJpaRepository profiles, Clock clock) {
        this.actor = Objects.requireNonNull(actor);
        this.users = Objects.requireNonNull(users);
        this.profiles = Objects.requireNonNull(profiles);
        this.clock = Objects.requireNonNull(clock);
    }

    public OrganizerProfileView getMine() {
        Long id = actor.requireUserId();
        return profiles.findById(id).map(this::view).orElseGet(() -> new OrganizerProfileView(id, null, null, null, null, null, null, null, null));
    }

    public OrganizerProfileView getPublic(Long organizerId) {
        return profiles.findById(Objects.requireNonNull(organizerId)).map(this::view)
            .orElseThrow(() -> new EntityNotFoundException("Organizer profile not found: " + organizerId));
    }

    @Transactional
    public OrganizerProfileView update(UpdateOrganizerProfileCommand command) {
        Objects.requireNonNull(command);
        Long organizerId = actor.requireUserId();
        if (!users.existsById(organizerId)) throw new EntityNotFoundException("Organizer account not found: " + organizerId);
        String organizationName = required(command.organizationName(), "organizationName");
        var profile = profiles.findById(organizerId).orElseGet(() -> new OrganizerProfileJpaEntity(
            organizerId, organizationName, clean(command.contactName()), clean(command.contactEmail()), clean(command.contactPhone()),
            clean(command.website()), clean(command.address()), clean(command.description()), clock.instant()
        ));
        profile.update(organizationName, clean(command.contactName()), normalizeEmail(command.contactEmail()), clean(command.contactPhone()),
            clean(command.website()), clean(command.address()), clean(command.description()), clock.instant());
        return view(profiles.save(profile));
    }

    private OrganizerProfileView view(OrganizerProfileJpaEntity profile) {
        return new OrganizerProfileView(profile.getOrganizerId(), profile.getOrganizationName(), profile.getContactName(),
            profile.getContactEmail(), profile.getContactPhone(), profile.getWebsite(), profile.getAddress(),
            profile.getDescription(), profile.getUpdatedAt());
    }

    private static String required(String value, String field) {
        String result = clean(value);
        if (result == null) throw new IllegalArgumentException(field + " is required");
        return result;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private static String normalizeEmail(String value) {
        String clean = clean(value);
        return clean == null ? null : clean.toLowerCase(java.util.Locale.ROOT);
    }
}
