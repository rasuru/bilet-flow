package com.biletflow.biletflow.eventmanagement.application.socialevent.view;

import com.biletflow.biletflow.eventmanagement.domain.EventVisibility;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventStatus;
import com.biletflow.biletflow.eventmanagement.domain.StaffRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ManagedEventView(
    UUID id,
    String title,
    String description,
    String category,
    String imageUrl,
    SocialEventStatus status,
    EventVisibility visibility,
    Instant startAt,
    Instant endAt,
    RegistrationWindowView registrationWindow,
    EventVenueView venue,
    List<StaffView> staff
) {
    public ManagedEventView {
        staff = List.copyOf(staff);
    }

    public record StaffView(Long userId, StaffRole role, Instant assignedAt) {}
}
