package com.biletflow.biletflow.eventmanagement.domain;

import com.biletflow.biletflow.eventmanagement.domain.exceptions.InvalidEventStateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SocialEvent {

    private final SocialEventId id;
    private final Long organizerId;

    private String title;
    private String description;
    private String category;
    private String imageUrl;

    private SocialEventStatus status;
    private EventVisibility visibility;
    private EventDateRange dateRange;
    private RegistrationWindow registrationWindow;
    private Venue venue;

    private final List<StaffAssignment> staffAssignments;

    private SocialEvent(
        SocialEventId id,
        Long organizerId,
        String title,
        String description,
        String category,
        String imageUrl,
        EventVisibility visibility,
        EventDateRange dateRange,
        RegistrationWindow registrationWindow
    ) {
        this.id = Objects.requireNonNull(id, "Id cannot be null");
        this.organizerId = Objects.requireNonNull(organizerId, "Organizer ID cannot be null");

        setTitle(title);
        setDescription(description);
        setCategory(category);

        this.imageUrl = imageUrl != null ? imageUrl.trim() : "";

        this.visibility = Objects.requireNonNull(visibility, "Visibility cannot be null");

        this.dateRange = Objects.requireNonNull(dateRange, "Date range cannot be null");

        this.registrationWindow = Objects.requireNonNull(registrationWindow, "Registration window cannot be null");

        this.status = SocialEventStatus.DRAFT;
        this.staffAssignments = new ArrayList<>();
    }

    public static SocialEvent createDraft(
        Long organizerId,
        String title,
        String description,
        String category,
        String imageUrl,
        EventVisibility visibility,
        EventDateRange dateRange,
        RegistrationWindow registrationWindow
    ) {
        return new SocialEvent(
            SocialEventId.generate(),
            organizerId,
            title,
            description,
            category,
            imageUrl,
            visibility,
            dateRange,
            registrationWindow
        );
    }

    public static SocialEvent rehydrate(
        SocialEventId id,
        Long organizerId,
        String title,
        String description,
        String category,
        String imageUrl,
        SocialEventStatus status,
        EventVisibility visibility,
        EventDateRange dateRange,
        RegistrationWindow registrationWindow,
        Venue venue,
        List<StaffAssignment> staffAssignments
    ) {
        SocialEvent event = new SocialEvent(
            id,
            organizerId,
            title,
            description,
            category,
            imageUrl,
            visibility,
            dateRange,
            registrationWindow
        );

        event.status = Objects.requireNonNull(status, "SocialEventStatus cannot be null");

        event.venue = venue;

        event.staffAssignments.clear();
        event.staffAssignments.addAll(Objects.requireNonNull(staffAssignments, "staffAssignments cannot be null"));

        return event;
    }

    public void publish() {
        if (status == SocialEventStatus.CANCELLED) {
            throw new InvalidEventStateException("Cannot publish a cancelled event");
        }

        if (venue == null) {
            throw new InvalidEventStateException("Cannot publish an event without a venue configured");
        }

        status = SocialEventStatus.PUBLISHED;
    }

    public void unpublish() {
        if (status != SocialEventStatus.PUBLISHED) {
            throw new InvalidEventStateException("Only published events can be unpublished");
        }

        status = SocialEventStatus.UNPUBLISHED;
    }

    public void cancel(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        if (dateRange.hasEnded(now)) {
            throw new InvalidEventStateException("Cannot cancel an event that has already ended");
        }

        if (status == SocialEventStatus.CANCELLED) {
            return;
        }

        status = SocialEventStatus.CANCELLED;
    }

    public void updateDetails(
        String title,
        String description,
        String category,
        String imageUrl,
        EventVisibility visibility,
        EventDateRange dateRange,
        RegistrationWindow registrationWindow
    ) {
        ensureNotCancelled();

        setTitle(title);
        setDescription(description);
        setCategory(category);

        this.imageUrl = imageUrl != null ? imageUrl.trim() : "";

        this.visibility = Objects.requireNonNull(visibility, "Visibility cannot be null");

        this.dateRange = Objects.requireNonNull(dateRange, "Date range cannot be null");

        this.registrationWindow = Objects.requireNonNull(registrationWindow, "Registration window cannot be null");
    }

    public void attachVenue(Venue venue) {
        ensureNotCancelled();

        if (status != SocialEventStatus.DRAFT) {
            throw new InvalidEventStateException("Venue and seating configuration can only be attached while the event is a draft");
        }

        if (this.venue != null) {
            throw new InvalidEventStateException("Venue and seating configuration are immutable once attached");
        }

        this.venue = Objects.requireNonNull(venue, "Venue cannot be null");
    }

    public void assignStaff(Long userId, StaffRole role, Instant now) {
        ensureNotCancelled();

        Objects.requireNonNull(userId, "Staff UserId cannot be null");

        Objects.requireNonNull(role, "StaffRole cannot be null");

        boolean alreadyAssigned = staffAssignments
            .stream()
            .anyMatch(assignment -> assignment.userId().equals(userId) && assignment.role() == role);

        if (!alreadyAssigned) {
            staffAssignments.add(StaffAssignment.create(userId, role, now));
        }
    }

    public boolean removeStaff(Long userId) {
        ensureNotCancelled();

        Objects.requireNonNull(userId, "Staff UserId cannot be null");

        return staffAssignments.removeIf(assignment -> assignment.userId().equals(userId));
    }

    public SocialEvent duplicate(EventDateRange newDateRange, RegistrationWindow newRegistrationWindow) {
        Objects.requireNonNull(newDateRange, "New date range cannot be null");

        Objects.requireNonNull(newRegistrationWindow, "New registration window cannot be null");

        SocialEvent copy = SocialEvent.createDraft(
            organizerId,
            title + " (Copy)",
            description,
            category,
            imageUrl,
            visibility,
            newDateRange,
            newRegistrationWindow
        );

        if (venue != null) {
            copy.attachVenue(venue.copy());
        }

        return copy;
    }

    public boolean canBeManagedBy(Long userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        return organizerId.equals(userId);
    }

    private void ensureNotCancelled() {
        if (status == SocialEventStatus.CANCELLED) {
            throw new InvalidEventStateException("Cannot modify a cancelled event");
        }
    }

    private void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Event title cannot be empty");
        }

        this.title = title.trim();
    }

    private void setDescription(String description) {
        this.description = description != null ? description.trim() : "";
    }

    private void setCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Event category cannot be empty");
        }

        this.category = category.trim();
    }

    public SocialEventId getId() {
        return id;
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public SocialEventStatus getStatus() {
        return status;
    }

    public EventVisibility getVisibility() {
        return visibility;
    }

    public EventDateRange getDateRange() {
        return dateRange;
    }

    public RegistrationWindow getRegistrationWindow() {
        return registrationWindow;
    }

    public Optional<Venue> getVenue() {
        return Optional.ofNullable(venue);
    }

    public List<StaffAssignment> getStaffAssignments() {
        return Collections.unmodifiableList(staffAssignments);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof SocialEvent other)) {
            return false;
        }

        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
