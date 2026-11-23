package com.biletflow.biletflow.iam.application.organizer;

import java.time.Instant;

public record OrganizerProfileView(
    Long organizerId,
    String organizationName,
    String contactName,
    String contactEmail,
    String contactPhone,
    String website,
    String address,
    String description,
    Instant updatedAt
) {}
