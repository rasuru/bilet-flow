package com.biletflow.biletflow.organizerhistory.application;

import java.util.UUID;

public interface EventAccessPort {
    boolean canManage(UUID eventId, Long userId);
}
