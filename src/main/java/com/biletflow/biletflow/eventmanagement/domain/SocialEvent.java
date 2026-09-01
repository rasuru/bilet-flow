package com.biletflow.biletflow.eventmanagement.domain;

import com.biletflow.biletflow.eventmanagement.domain.exceptions.InvalidEventStateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    private final List<TicketType> ticketTypes;
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
        RegistrationWindow registrationWindow,
        Long actorUserId
    ) {
        this.id = Objects.requireNonNull(id, "Id cannot be null");
        this.organizerId = Objects.requireNonNull(organizerId, "Organizer ID cannot be null");
        this.setTitle(title);
        this.setDescription(description);
        this.setCategory(category);
        this.imageUrl = imageUrl != null ? imageUrl.trim() : "";
        this.visibility = Objects.requireNonNull(visibility, "Visibility cannot be null");
        this.dateRange = Objects.requireNonNull(dateRange, "Date range cannot be null");
        this.registrationWindow = Objects.requireNonNull(registrationWindow, "Registration window cannot be null");

        this.status = SocialEventStatus.DRAFT;
        this.ticketTypes = new ArrayList<>();
        this.staffAssignments = new ArrayList<>();
    }

    // --- Factory Method for Draft Initialization ---

    public static SocialEvent createDraft(
        Long organizerId,
        String title,
        String description,
        String category,
        String imageUrl,
        EventVisibility visibility,
        EventDateRange dateRange,
        RegistrationWindow registrationWindow,
        Long actorUserId
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
            registrationWindow,
            actorUserId
        );
    }

    // --- Domain Behaviors & Lifecycle Invariants ---

    public void publish(Long actorUserId) {
        if (this.status == SocialEventStatus.CANCELLED) {
            throw new InvalidEventStateException("Cannot publish a cancelled event.");
        }
        if (this.venue == null) {
            throw new InvalidEventStateException("Cannot publish an event without a venue configured.");
        }
        if (this.ticketTypes.isEmpty()) {
            throw new InvalidEventStateException("Cannot publish an event without at least one ticket type.");
        }

        this.status = SocialEventStatus.PUBLISHED;
    }

    public void unpublish(Long actorUserId) {
        if (this.status != SocialEventStatus.PUBLISHED) {
            throw new InvalidEventStateException("Only published events can be unpublished.");
        }
        this.status = SocialEventStatus.UNPUBLISHED;
    }

    public void cancel(Instant now, Long actorUserId) {
        if (this.dateRange.hasEnded(now)) {
            throw new InvalidEventStateException("Cannot cancel an event that has already ended.");
        }
        if (this.status == SocialEventStatus.CANCELLED) {
            return;
        }
        this.status = SocialEventStatus.CANCELLED;
    }

    public void updateDetails(
        String title,
        String description,
        String category,
        String imageUrl,
        EventVisibility visibility,
        EventDateRange dateRange,
        RegistrationWindow registrationWindow,
        Long actorUserId
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

    // --- Aggregate Child Entity Operations ---

    public void attachVenue(Venue venue, Long actorUserId) {
        ensureNotCancelled();
        this.venue = Objects.requireNonNull(venue, "Venue cannot be null");
    }

    public void addTicketType(
        String name,
        String description,
        TicketPricing pricing,
        int quantity,
        SalesWindow salesWindow,
        int maxPerOrder,
        Long actorUserId
    ) {
        ensureNotCancelled();
        TicketType ticketType = TicketType.create(name, description, pricing, quantity, salesWindow, maxPerOrder);
        this.ticketTypes.add(ticketType);
    }

    public void hideTicketType(TicketTypeId ticketTypeId, Long actorUserId) {
        ensureNotCancelled();
        TicketType ticketType = findTicketTypeOrThrow(ticketTypeId);
        ticketType.hide();
    }

    public void revealTicketType(TicketTypeId ticketTypeId, Long actorUserId) {
        ensureNotCancelled();
        TicketType ticketType = findTicketTypeOrThrow(ticketTypeId);
        ticketType.reveal();
    }

    public void assignStaff(Long userId, StaffRole role, Long actorUserId) {
        ensureNotCancelled();
        Objects.requireNonNull(userId, "Staff UserId cannot be null");
        Objects.requireNonNull(role, "StaffRole cannot be null");

        boolean alreadyAssigned = staffAssignments
            .stream()
            .anyMatch(assignment -> assignment.userId().equals(userId) && assignment.role() == role);

        if (!alreadyAssigned) {
            staffAssignments.add(StaffAssignment.create(userId, role));
        }
    }

    public boolean removeStaff(Long userId, Long actorUserId) {
        ensureNotCancelled();
        return staffAssignments.removeIf(assignment -> assignment.userId().equals(userId));
    }

    // --- Duplication Business Method ---

    public SocialEvent duplicate(Instant now, EventDateRange newDateRange, RegistrationWindow newRegistrationWindow, Long actorUserId) {
        SocialEvent copy = SocialEvent.createDraft(
            this.organizerId,
            this.title + " (Copy)",
            this.description,
            this.category,
            this.imageUrl,
            this.visibility,
            newDateRange,
            newRegistrationWindow,
            actorUserId
        );

        if (this.venue != null) {
            copy.attachVenue(this.venue, actorUserId);
        }

        for (TicketType existing : this.ticketTypes) {
            copy.addTicketType(
                existing.getName(),
                existing.getDescription(),
                existing.getPricing(),
                existing.getTotalQuantity(),
                existing.getSalesWindow(),
                existing.getMaxPerOrder(),
                actorUserId
            );
        }

        return copy;
    }

    // --- Private Helper Methods ---

    private void ensureNotCancelled() {
        if (this.status == SocialEventStatus.CANCELLED) {
            throw new InvalidEventStateException("Cannot modify a cancelled event.");
        }
    }

    private TicketType findTicketTypeOrThrow(TicketTypeId ticketTypeId) {
        return this.ticketTypes
            .stream()
            .filter(tt -> tt.getId().equals(ticketTypeId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("TicketType not found: " + ticketTypeId.value()));
    }

    private void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Event title cannot be empty.");
        }
        this.title = title.trim();
    }

    private void setDescription(String description) {
        this.description = description != null ? description.trim() : "";
    }

    private void setCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Event category cannot be empty.");
        }
        this.category = category.trim();
    }

    // --- Read-Only Getters ---

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

    public List<TicketType> getTicketTypes() {
        return Collections.unmodifiableList(ticketTypes);
    }

    public List<StaffAssignment> getStaffAssignments() {
        return Collections.unmodifiableList(staffAssignments);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SocialEvent that = (SocialEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
