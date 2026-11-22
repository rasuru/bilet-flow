package com.biletflow.biletflow.common.ohs;

import java.util.List;
import java.util.UUID;

/** Published read contract for contexts that contribute to the platform operations report. */
public interface OperationalCountsOhs {
    record Count(String resource, String status, long count) {}

    List<Count> countByStatus(UUID eventId);
}
