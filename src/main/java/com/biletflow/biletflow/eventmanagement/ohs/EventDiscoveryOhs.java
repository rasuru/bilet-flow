package com.biletflow.biletflow.eventmanagement.ohs;

import com.biletflow.biletflow.eventmanagement.ohs.dto.DiscoveryEvent;
import java.time.Instant;
import java.util.List;

public interface EventDiscoveryOhs {
    List<DiscoveryEvent> findCandidates(Instant now, String query, String category, boolean descending);
}
