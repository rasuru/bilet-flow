package com.biletflow.biletflow.iam.application.organizer;

public record UpdateOrganizerProfileCommand(
    String organizationName,
    String contactName,
    String contactEmail,
    String contactPhone,
    String website,
    String address,
    String description
) {}
