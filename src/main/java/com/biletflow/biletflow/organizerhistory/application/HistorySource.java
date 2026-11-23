package com.biletflow.biletflow.organizerhistory.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface HistorySource {
    List<HistoryEntry> find(UUID eventId, Instant from, Instant to);
}
