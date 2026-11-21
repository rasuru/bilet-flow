package com.biletflow.biletflow.common.persistence;

import com.biletflow.biletflow.common.ohs.OperationalCountsOhs.Count;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OperationalCountReader {

    private final NamedParameterJdbcTemplate jdbc;

    public OperationalCountReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The owning context supplies a fixed SELECT projecting status and event_id. */
    public List<Count> count(String resource, String records, UUID eventId) {
        String scope = eventId == null ? "" : " where event_id = :eventId";
        String query = "select status, count(*) as total from (" + records + ") report_rows" + scope + " group by status";
        var parameters = new MapSqlParameterSource();
        if (eventId != null) parameters.addValue("eventId", eventId);
        return jdbc.query(query, parameters, (row, index) -> new Count(resource, row.getString("status"), row.getLong("total")));
    }
}
