package com.biletflow.biletflow.support.persistence;

import com.biletflow.biletflow.common.ohs.OperationalCountsOhs;
import com.biletflow.biletflow.common.persistence.OperationalCountReader;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class SupportOperationalCounts implements OperationalCountsOhs {

    private final OperationalCountReader reader;

    public SupportOperationalCounts(OperationalCountReader reader) {
        this.reader = reader;
    }

    @Override
    public List<Count> countByStatus(UUID eventId) {
        return reader.count(
            "platform_support_cases",
            """
            select status, event_id from support_case
            where case_type = 'PLATFORM_SUPPORT' or escalated_at is not null
            """,
            eventId
        );
    }
}
