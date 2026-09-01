package com.biletflow.biletflow.eventmanagement.domain;

// From the requirements 4.16:
// "The organizer dashboard shall classify events as
// Upcoming, Active, Completed, or Canceled."
// As you can see this status is a bit different.
// The reason for that is that Upcoming, Active, and Completed
// states will probably be derived from the event date range.
public enum SocialEventStatus {
    DRAFT, // only visible to the organizer
    PUBLISHED,
    UNPUBLISHED,
    CANCELLED,
}
