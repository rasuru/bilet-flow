package com.biletflow.biletflow.iam.application.organizer;

import java.util.Optional;

public interface OrganizerProfileRepository {
    boolean organizerExists(Long organizerId);
    Optional<OrganizerProfileView> findByOrganizerId(Long organizerId);
    OrganizerProfileView save(Long organizerId, UpdateOrganizerProfileCommand command);
}
