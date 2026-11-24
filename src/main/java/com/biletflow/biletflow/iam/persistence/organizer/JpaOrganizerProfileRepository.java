package com.biletflow.biletflow.iam.persistence.organizer;

import com.biletflow.biletflow.iam.application.organizer.*;
import com.biletflow.biletflow.iam.persistence.UserRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaOrganizerProfileRepository implements OrganizerProfileRepository {
    private final OrganizerProfileJpaRepository jpa;
    private final UserRepository users;
    private final Clock clock;

    public JpaOrganizerProfileRepository(OrganizerProfileJpaRepository jpa, UserRepository users, Clock clock) {
        this.jpa = Objects.requireNonNull(jpa);
        this.users = Objects.requireNonNull(users);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public boolean organizerExists(Long organizerId) { return users.existsById(organizerId); }

    @Override
    public Optional<OrganizerProfileView> findByOrganizerId(Long id) {
        return jpa.findById(id).map(this::view);
    }

    @Override
    @Transactional
    public OrganizerProfileView save(Long id, UpdateOrganizerProfileCommand command) {
        String name = clean(command.organizationName());
        OrganizerProfileJpaEntity profile = jpa.findById(id).orElseGet(() -> new OrganizerProfileJpaEntity(
            id, name, clean(command.contactName()), normalizeEmail(command.contactEmail()), clean(command.contactPhone()),
            clean(command.website()), clean(command.address()), clean(command.description()), clock.instant()));
        profile.update(name, clean(command.contactName()), normalizeEmail(command.contactEmail()), clean(command.contactPhone()),
            clean(command.website()), clean(command.address()), clean(command.description()), clock.instant());
        return view(jpa.save(profile));
    }

    private OrganizerProfileView view(OrganizerProfileJpaEntity p) {
        return new OrganizerProfileView(p.getOrganizerId(), p.getOrganizationName(), p.getContactName(), p.getContactEmail(),
            p.getContactPhone(), p.getWebsite(), p.getAddress(), p.getDescription(), p.getUpdatedAt());
    }
    private static String clean(String v) { if (v == null) return null; String s = v.trim(); return s.isEmpty() ? null : s; }
    private static String normalizeEmail(String v) { String s = clean(v); return s == null ? null : s.toLowerCase(java.util.Locale.ROOT); }
}
