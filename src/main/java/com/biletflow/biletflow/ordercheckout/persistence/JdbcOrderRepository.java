package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.common.persistence.JsonCodec;
import com.biletflow.biletflow.ordercheckout.domain.order.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcOrderRepository implements OrderRepository {

    private final JdbcTemplate jdbc;
    private final JsonCodec json;

    public JdbcOrderRepository(JdbcTemplate jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Order save(Order o) {
        jdbc.update(
            "insert into oc_order(id,event_id,owner_id,snapshot) values (?,?,?,?) on conflict(id) do update set snapshot=excluded.snapshot",
            o.getId(),
            o.getEventId(),
            o.getOwnerId(),
            json.write(CheckoutSnapshots.Sale.of(o))
        );
        return o;
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return query("select snapshot from oc_order where id=?", id.value()).stream().findFirst();
    }

    @Override
    public List<Order> findAllByEventId(UUID id) {
        return query("select snapshot from oc_order where event_id=?", id);
    }

    @Override
    public List<Order> findAllByOwnerUserId(Long id) {
        return query("select snapshot from oc_order where owner_id=?", id);
    }

    private List<Order> query(String sql, Object id) {
        return jdbc.query(sql, (rs, n) -> json.read(rs.getString(1), CheckoutSnapshots.Sale.class).domain(), id);
    }
}
