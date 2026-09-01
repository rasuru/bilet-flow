package com.biletflow.biletflow.eventmanagement.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "social_event")
public class SocialEventEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "visibility", nullable = false)
    private String visibility;

    // --- Date Range ---
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    // --- Registration Window ADT Flattened ---
    @Column(name = "reg_window_type", nullable = false)
    private String regWindowType;

    @Column(name = "reg_window_start")
    private Instant regWindowStart;

    @Column(name = "reg_window_end")
    private Instant regWindowEnd;

    // --- Embedded Venue ---
    @Column(name = "venue_name")
    private String venueName;

    @Column(name = "venue_address")
    private String venueAddress;

    @Column(name = "venue_capacity")
    private Integer venueCapacity;

    @Column(name = "venue_seating_type")
    private String venueSeatingType;

    @Column(name = "venue_layout_id")
    private String venueLayoutId;

    // --- Relationships ---
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private List<TicketTypeEntity> ticketTypes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "social_event_staff", joinColumns = @JoinColumn(name = "event_id"))
    private List<StaffAssignmentEmbeddable> staffAssignments = new ArrayList<>();

    public SocialEventEntity() {}

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(Long organizerId) {
        this.organizerId = organizerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public String getRegWindowType() {
        return regWindowType;
    }

    public void setRegWindowType(String regWindowType) {
        this.regWindowType = regWindowType;
    }

    public Instant getRegWindowStart() {
        return regWindowStart;
    }

    public void setRegWindowStart(Instant regWindowStart) {
        this.regWindowStart = regWindowStart;
    }

    public Instant getRegWindowEnd() {
        return regWindowEnd;
    }

    public void setRegWindowEnd(Instant regWindowEnd) {
        this.regWindowEnd = regWindowEnd;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public String getVenueAddress() {
        return venueAddress;
    }

    public void setVenueAddress(String venueAddress) {
        this.venueAddress = venueAddress;
    }

    public Integer getVenueCapacity() {
        return venueCapacity;
    }

    public void setVenueCapacity(Integer venueCapacity) {
        this.venueCapacity = venueCapacity;
    }

    public String getVenueSeatingType() {
        return venueSeatingType;
    }

    public void setVenueSeatingType(String venueSeatingType) {
        this.venueSeatingType = venueSeatingType;
    }

    public String getVenueLayoutId() {
        return venueLayoutId;
    }

    public void setVenueLayoutId(String venueLayoutId) {
        this.venueLayoutId = venueLayoutId;
    }

    public List<TicketTypeEntity> getTicketTypes() {
        return ticketTypes;
    }

    public void setTicketTypes(List<TicketTypeEntity> ticketTypes) {
        this.ticketTypes = ticketTypes;
    }

    public List<StaffAssignmentEmbeddable> getStaffAssignments() {
        return staffAssignments;
    }

    public void setStaffAssignments(List<StaffAssignmentEmbeddable> staffAssignments) {
        this.staffAssignments = staffAssignments;
    }
}
