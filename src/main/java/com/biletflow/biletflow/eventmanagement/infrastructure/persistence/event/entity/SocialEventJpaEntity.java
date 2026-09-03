package com.biletflow.biletflow.eventmanagement.infrastructure.persistence.event.entity;

import com.biletflow.biletflow.eventmanagement.domain.EventVisibility;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "event_management_event",
    indexes = {
        @Index(name = "idx_event_management_event_organizer", columnList = "organizer_id"),
        @Index(name = "idx_event_management_event_status", columnList = "status"),
    }
)
public class SocialEventJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organizer_id", nullable = false, updatable = false)
    private Long organizerId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "image_url", nullable = false, length = 1024)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SocialEventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private EventVisibility visibility;

    @Column(name = "event_start_at", nullable = false)
    private Instant eventStartAt;

    @Column(name = "event_end_at", nullable = false)
    private Instant eventEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_window_mode", nullable = false, length = 32)
    private RegistrationWindowModeJpa registrationWindowMode;

    @Column(name = "registration_start_at")
    private Instant registrationStartAt;

    @Column(name = "registration_end_at")
    private Instant registrationEndAt;

    @Column(name = "venue_name", length = 255)
    private String venueName;

    @Column(name = "venue_address", length = 1024)
    private String venueAddress;

    @Column(name = "venue_total_capacity")
    private Integer venueTotalCapacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "seating_mode", length = 32)
    private SeatingModeJpa seatingMode;

    @Column(name = "venue_layout_id")
    private UUID venueLayoutId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "event_management_event_staff",
        joinColumns = @JoinColumn(name = "event_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_event_management_event_staff", columnNames = { "event_id", "user_id", "role" })
    )
    private List<StaffAssignmentJpaEmbeddable> staffAssignments = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SocialEventJpaEntity() {}

    public SocialEventJpaEntity(UUID id, Long organizerId) {
        this.id = id;
        this.organizerId = organizerId;
    }

    public UUID getId() {
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

    public Instant getEventStartAt() {
        return eventStartAt;
    }

    public Instant getEventEndAt() {
        return eventEndAt;
    }

    public RegistrationWindowModeJpa getRegistrationWindowMode() {
        return registrationWindowMode;
    }

    public Instant getRegistrationStartAt() {
        return registrationStartAt;
    }

    public Instant getRegistrationEndAt() {
        return registrationEndAt;
    }

    public String getVenueName() {
        return venueName;
    }

    public String getVenueAddress() {
        return venueAddress;
    }

    public Integer getVenueTotalCapacity() {
        return venueTotalCapacity;
    }

    public SeatingModeJpa getSeatingMode() {
        return seatingMode;
    }

    public UUID getVenueLayoutId() {
        return venueLayoutId;
    }

    public List<StaffAssignmentJpaEmbeddable> getStaffAssignments() {
        return staffAssignments;
    }

    public long getVersion() {
        return version;
    }

    public void setCoreState(
        String title,
        String description,
        String category,
        String imageUrl,
        SocialEventStatus status,
        EventVisibility visibility,
        Instant eventStartAt,
        Instant eventEndAt
    ) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.imageUrl = imageUrl;
        this.status = status;
        this.visibility = visibility;
        this.eventStartAt = eventStartAt;
        this.eventEndAt = eventEndAt;
    }

    public void setRegistrationWindow(RegistrationWindowModeJpa mode, Instant startAt, Instant endAt) {
        this.registrationWindowMode = mode;
        this.registrationStartAt = startAt;
        this.registrationEndAt = endAt;
    }

    public void clearVenue() {
        this.venueName = null;
        this.venueAddress = null;
        this.venueTotalCapacity = null;
        this.seatingMode = null;
        this.venueLayoutId = null;
    }

    public void setGeneralAdmissionVenue(String name, String address, int capacity) {
        this.venueName = name;
        this.venueAddress = address;
        this.venueTotalCapacity = capacity;
        this.seatingMode = SeatingModeJpa.GENERAL_ADMISSION;
        this.venueLayoutId = null;
    }

    public void setAssignedSeatingVenue(String name, String address, int capacity, UUID layoutId) {
        this.venueName = name;
        this.venueAddress = address;
        this.venueTotalCapacity = capacity;
        this.seatingMode = SeatingModeJpa.ASSIGNED_SEATING;
        this.venueLayoutId = layoutId;
    }

    public void replaceStaffAssignments(List<StaffAssignmentJpaEmbeddable> assignments) {
        this.staffAssignments.clear();
        this.staffAssignments.addAll(assignments);
    }
}
