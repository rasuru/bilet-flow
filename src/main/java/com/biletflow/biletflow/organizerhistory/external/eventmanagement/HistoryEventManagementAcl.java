package com.biletflow.biletflow.organizerhistory.external.eventmanagement;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.organizerhistory.application.EventAccessPort;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HistoryEventManagementAcl implements EventAccessPort {

    private final EventManagementOhs eventManagement;

    public HistoryEventManagementAcl(EventManagementOhs eventManagement) {
        this.eventManagement = Objects.requireNonNull(eventManagement, "eventManagement cannot be null");
    }

    @Override
    public boolean canManage(UUID eventId, Long userId) {
        try {
            return eventManagement.canManageTicketing(eventId, userId);
        } catch (SocialEventNotFoundException exception) {
            throw new EntityNotFoundException("Event not found: " + eventId);
        }
    }
}
