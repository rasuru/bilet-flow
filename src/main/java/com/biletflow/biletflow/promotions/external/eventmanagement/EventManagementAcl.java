package com.biletflow.biletflow.promotions.external.eventmanagement;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.promotions.application.eventmanagement.port.EventManagementPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("promotionsEventManagementAcl")
public class EventManagementAcl implements EventManagementPort {

    private final EventManagementOhs eventManagementOhs;
    private final Clock clock;

    public EventManagementAcl(EventManagementOhs eventManagementOhs, Clock clock) {
        this.eventManagementOhs = Objects.requireNonNull(eventManagementOhs);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public boolean canManagePromotions(UUID eventId, Long userId) {
        try {
            return eventManagementOhs.canManageTicketing(eventId, userId);
        } catch (SocialEventNotFoundException exception) {
            throw new EntityNotFoundException("Event not found: " + eventId);
        }
    }

    @Override
    public boolean isCancelled(UUID eventId) {
        try {
            return eventManagementOhs.salesScope(eventId, Instant.now(clock)).cancelled();
        } catch (SocialEventNotFoundException exception) {
            throw new EntityNotFoundException("Event not found: " + eventId);
        }
    }
}
