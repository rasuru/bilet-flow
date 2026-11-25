package com.biletflow.biletflow.eventmanagement.persistence.event.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "em_attendee_invitation")
public class AttendeeInvitationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "email", nullable = false, updatable = false, length = 254)
    private String email;

    @Column(name = "token", nullable = false, updatable = false)
    private UUID token;

    @Column(name = "accepted_by")
    private Long acceptedBy;

    protected AttendeeInvitationJpaEntity() {}

    public AttendeeInvitationJpaEntity(UUID id, UUID eventId, String email, UUID token) {
        this.id = id;
        this.eventId = eventId;
        this.email = email;
        this.token = token;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEmail() {
        return email;
    }

    public UUID getToken() {
        return token;
    }

    public Long getAcceptedBy() {
        return acceptedBy;
    }

    public void setAcceptedBy(long userId) {
        acceptedBy = userId;
    }
}
