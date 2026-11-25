package com.biletflow.biletflow.eventmanagement.application.socialevent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendeeInvitationRepository {
    record Invitation(UUID id, UUID eventId, String email, UUID token, Long acceptedBy) {
        @Override
        public String toString() {
            return "Invitation[id=" + id + "]";
        }
    }

    List<Invitation> findByEvent(UUID eventId);
    Optional<Invitation> findById(UUID eventId, UUID id);
    Optional<Invitation> findByEmail(UUID eventId, String email);
    Optional<Invitation> findByToken(UUID eventId, UUID token);
    boolean hasAccepted(UUID eventId, long userId);
    void insert(Invitation invitation);
    void accept(UUID id, long userId);
    boolean delete(UUID eventId, UUID id);
}
