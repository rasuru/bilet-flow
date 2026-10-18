package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.common.persistence.JsonCodec;
import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcCheckoutSessionRepository implements CheckoutSessionRepository {

    private final JdbcTemplate jdbc;
    private final JsonCodec json;

    public JdbcCheckoutSessionRepository(JdbcTemplate jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CheckoutSession save(CheckoutSession c) {
        jdbc.update(
            "insert into oc_checkout(id,event_id,owner_id,status,snapshot) values (?,?,?,?,?) on conflict(id) do update set status=excluded.status,snapshot=excluded.snapshot",
            c.getId().value(),
            c.getEventId(),
            c.getOwnerId(),
            c.getStatus().name(),
            json.write(CheckoutSnapshots.Session.of(c))
        );
        return c;
    }

    @Override
    public Optional<CheckoutSession> findById(CheckoutSessionId id) {
        return query("select snapshot from oc_checkout where id=?", id.value()).stream().findFirst();
    }

    @Override
    public List<CheckoutSession> findAllByEventId(UUID id) {
        return query("select snapshot from oc_checkout where event_id=?", id);
    }

    @Override
    public List<CheckoutSession> findAllByOwnerUserId(Long id) {
        return query("select snapshot from oc_checkout where owner_id=?", id);
    }

    private List<CheckoutSession> query(String sql, Object id) {
        return jdbc.query(sql, (rs, n) -> json.read(rs.getString(1), CheckoutSnapshots.Session.class).domain(), id);
    }
}
