package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.common.persistence.JsonCodec;
import com.biletflow.biletflow.ordercheckout.application.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcCheckoutProcessRepository implements CheckoutProcessRepository {

    private final JdbcTemplate jdbc;
    private final JsonCodec json;

    public JdbcCheckoutProcessRepository(JdbcTemplate jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<CheckoutProcess> find(UUID id) {
        return jdbc
            .query("select snapshot from oc_saga where id=?", (rs, n) -> json.read(rs.getString(1), CheckoutProcess.class), id)
            .stream()
            .findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(CheckoutProcess p) {
        jdbc.update(
            "insert into oc_saga(id,snapshot) values (?,?) on conflict(id) do update set snapshot=excluded.snapshot",
            p.id(),
            json.write(p)
        );
    }

    @Override
    public List<UUID> active() {
        return jdbc.query("select id from oc_checkout where status in ('IN_PROGRESS','AWAITING_PAYMENT') order by id", (rs, n) ->
            rs.getObject(1, UUID.class)
        );
    }
}
