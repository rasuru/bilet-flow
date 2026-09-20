package com.biletflow.biletflow.ordercheckout.application.eventmanagement.port;

import java.util.UUID;

public interface EventManagementPort {
    EventTicketingConfiguration getTicketingConfiguration(UUID eventId);

    boolean canManageTicketing(UUID eventId, Long userId);
}
