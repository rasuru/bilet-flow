package com.biletflow.biletflow.promotions.application.eventmanagement.port;

import java.util.UUID;

public interface EventManagementPort {
    boolean canManagePromotions(UUID eventId, Long userId);

    boolean isCancelled(UUID eventId);
}
