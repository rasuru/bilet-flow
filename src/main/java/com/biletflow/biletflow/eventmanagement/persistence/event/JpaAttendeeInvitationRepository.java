package com.biletflow.biletflow.eventmanagement.persistence.event;

import com.biletflow.biletflow.eventmanagement.application.socialevent.AttendeeInvitationRepository;
import com.biletflow.biletflow.eventmanagement.persistence.event.entity.AttendeeInvitationJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAttendeeInvitationRepository implements AttendeeInvitationRepository {

    private static final String FOR_EVENT = "from AttendeeInvitationJpaEntity i where i.eventId = :eventId";
    private final EntityManager entities;

    public JpaAttendeeInvitationRepository(EntityManager entities) {
        this.entities = entities;
    }

    @Override
    public List<Invitation> findByEvent(UUID eventId) {
        return entities
            .createQuery(FOR_EVENT + " order by i.email", AttendeeInvitationJpaEntity.class)
            .setParameter("eventId", eventId)
            .getResultList()
            .stream()
            .map(JpaAttendeeInvitationRepository::toInvitation)
            .toList();
    }

    @Override
    public Optional<Invitation> findById(UUID eventId, UUID id) {
        return findEntity(eventId, id).map(JpaAttendeeInvitationRepository::toInvitation);
    }

    @Override
    public Optional<Invitation> findByEmail(UUID eventId, String email) {
        return Optional.ofNullable(
            entities
                .createQuery(FOR_EVENT + " and i.email = :email", AttendeeInvitationJpaEntity.class)
                .setParameter("eventId", eventId)
                .setParameter("email", email)
                .getSingleResultOrNull()
        ).map(JpaAttendeeInvitationRepository::toInvitation);
    }

    @Override
    public Optional<Invitation> findByToken(UUID eventId, UUID token) {
        return Optional.ofNullable(
            entities
                .createQuery(FOR_EVENT + " and i.token = :token", AttendeeInvitationJpaEntity.class)
                .setParameter("eventId", eventId)
                .setParameter("token", token)
                .getSingleResultOrNull()
        ).map(JpaAttendeeInvitationRepository::toInvitation);
    }

    @Override
    public boolean hasAccepted(UUID eventId, long userId) {
        return !entities
            .createQuery("select i.id " + FOR_EVENT + " and i.acceptedBy = :userId", UUID.class)
            .setParameter("eventId", eventId)
            .setParameter("userId", userId)
            .setMaxResults(1)
            .getResultList()
            .isEmpty();
    }

    @Override
    public void insert(Invitation invitation) {
        entities.persist(new AttendeeInvitationJpaEntity(invitation.id(), invitation.eventId(), invitation.email(), invitation.token()));
    }

    @Override
    public void accept(UUID id, long userId) {
        entities.find(AttendeeInvitationJpaEntity.class, id).setAcceptedBy(userId);
    }

    @Override
    public boolean delete(UUID eventId, UUID id) {
        var invitation = findEntity(eventId, id);
        invitation.ifPresent(entities::remove);
        return invitation.isPresent();
    }

    private Optional<AttendeeInvitationJpaEntity> findEntity(UUID eventId, UUID id) {
        return Optional.ofNullable(entities.find(AttendeeInvitationJpaEntity.class, id)).filter(invitation ->
            invitation.getEventId().equals(eventId)
        );
    }

    private static Invitation toInvitation(AttendeeInvitationJpaEntity entity) {
        return new Invitation(entity.getId(), entity.getEventId(), entity.getEmail(), entity.getToken(), entity.getAcceptedBy());
    }
}
