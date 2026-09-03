package com.biletflow.biletflow.eventmanagement.domain;

import java.util.List;
import java.util.Optional;

public interface VenueLayoutRepository {
    VenueLayout save(VenueLayout layout);

    Optional<VenueLayout> findById(VenueLayoutId id);

    List<VenueLayout> findAll();
}
