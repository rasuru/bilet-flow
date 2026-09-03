package com.biletflow.biletflow.eventmanagement.domain;

import java.util.List;
import java.util.Optional;

public interface SocialEventRepository {
    SocialEvent save(SocialEvent socialEvent);

    Optional<SocialEvent> findById(SocialEventId id);

    List<SocialEvent> findByOrganizerId(Long organizerId);

    boolean existsById(SocialEventId id);

    void deleteById(SocialEventId id);
}
