package com.biletflow.biletflow.paymentsmonetization.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.common.persistence.JsonCodec;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.*;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Runs both Liquibase versions in an isolated schema with representative pre-migration data. */
@IntegrationTest
class PaymentSchemaMigrationIT {

    @Autowired
    DataSource dataSource;

    @Autowired
    JsonCodec json;

    @Test
    void existingPurchasesRefundHistoryAndEncryptedFormsSurviveMigration() throws Exception {
        String schema = "payment_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection()) {
            String originalSchema = connection.getSchema();
            try {
                connection.createStatement().execute("create schema " + schema);
                connection.setSchema(schema);
                var isolated = new SingleConnectionDataSource(connection, true);
                var jdbc = new JdbcTemplate(isolated);
                migrate(isolated, schema, "20261018190000_payment_saga.xml");
                UUID checkout = UUID.randomUUID(),
                    payment = UUID.randomUUID(),
                    event = UUID.randomUUID(),
                    type = UUID.randomUUID();
                UUID firstSeat = UUID.randomUUID(),
                    secondSeat = UUID.randomUUID(),
                    firstRefund = UUID.randomUUID(),
                    currentRefund = UUID.randomUUID();
                var price = Map.of("amount", new BigDecimal("1000.00"), "currency", "KZT");
                var zero = Map.of("amount", new BigDecimal("0.00"), "currency", "KZT");
                var purchase = new LinkedHashMap<String, Object>();
                purchase.put("id", checkout);
                purchase.put("eventId", event);
                purchase.put("ownerId", 1);
                purchase.put("status", "COMPLETED");
                purchase.put("mode", "RESERVED_SEATING");
                purchase.put("discount", zero);
                purchase.put("total", Map.of("amount", new BigDecimal("2000.00"), "currency", "KZT"));
                purchase.put("paymentId", payment);
                purchase.put(
                    "items",
                    List.of(
                        Map.of("ticketTypeId", type, "seatId", firstSeat, "price", price),
                        Map.of("ticketTypeId", type, "seatId", secondSeat, "price", price)
                    )
                );
                jdbc.update(
                    "insert into oc_checkout(id,event_id,owner_id,status,snapshot) values (?,?,1,'COMPLETED',?)",
                    checkout,
                    event,
                    json.write(purchase)
                );
                jdbc.update("insert into oc_order(id,event_id,owner_id,snapshot) values (?,?,1,?)", checkout, event, json.write(purchase));
                var saga = new LinkedHashMap<String, Object>();
                saga.put("eventId", event);
                saga.put("ownerId", 1);
                saga.put("fingerprint", "a".repeat(64));
                saga.put("deadline", "2026-10-18T12:10:00Z");
                saga.put("paymentId", payment);
                saga.put("refundState", "PENDING");
                saga.put("refundId", currentRefund);
                saga.put(
                    "lines",
                    List.of(
                        Map.of("ticketTypeId", type, "seatId", firstSeat, "price", price, "email", "first@example.com"),
                        Map.of("ticketTypeId", type, "seatId", secondSeat, "price", price, "email", "second@example.com")
                    )
                );
                saga.put(
                    "holds",
                    List.of(Map.of("referenceId", firstSeat, "assignedSeat", true), Map.of("referenceId", secondSeat, "assignedSeat", true))
                );
                jdbc.update("insert into oc_saga(id,snapshot) values (?,?)", checkout, json.write(saga));
                var work = new LinkedHashMap<String, Object>();
                work.put("amount", new BigDecimal("2000.00"));
                work.put("fee", new BigDecimal("60.00"));
                work.put("callbackSecret", "preserved-secret");
                work.put("deadline", "2026-10-18T12:10:00Z");
                work.put("status", "SUCCEEDED");
                work.put("paidAt", "2026-10-18T12:00:00Z");
                work.put("providerReference", "bank-transaction");
                work.put("bankTransactionId", "bank-transaction");
                work.put("operationId", currentRefund);
                work.put("operation", "refund");
                work.put("operationState", "UNKNOWN");
                work.put("operationAcknowledged", true);
                work.put("reviewRequired", true);
                work.put(
                    "refund",
                    Map.of("id", currentRefund, "reason", "Retry", "requestedAt", "2026-10-18T12:02:00Z", "status", "REQUESTED")
                );
                jdbc.update(
                    "insert into pm_payment_work(id,order_id,event_id,organizer_id,payer_id,purpose,invoice_id,snapshot,next_poll) values (?,?,?,7,1,'TICKET','543210',?,current_timestamp)",
                    payment,
                    checkout,
                    event,
                    json.write(work)
                );
                jdbc.update(
                    "insert into pm_refund_history(id,payment_id,reason,requested_at,status,failure_reason) values (?,?,'First',current_timestamp,'FAILED','Rejected')",
                    firstRefund,
                    payment
                );
                jdbc.update(
                    "insert into pm_payment_session(payment_id,claim_id,claimed_until,encrypted_payload,expires_at) values (?,?,current_timestamp,'existing-encrypted-form',current_timestamp)",
                    payment,
                    UUID.randomUUID()
                );
                jdbc.queryForObject("select setval('pm_halyk_invoice_seq',543210)", Long.class);
                migrate(isolated, schema, "20261019010000_jpa_payments.xml");
                assertEquals(
                    new BigDecimal("2000.00"),
                    jdbc.queryForObject("select amount from pm_payment_work where id=?", BigDecimal.class, payment)
                );
                assertEquals(
                    currentRefund,
                    jdbc.queryForObject("select current_refund_id from pm_payment_work where id=?", UUID.class, payment)
                );
                assertEquals(
                    "UNKNOWN",
                    jdbc.queryForObject("select operation_state from pm_payment_work where id=?", String.class, payment)
                );
                assertTrue(jdbc.queryForObject("select operation_acknowledged from pm_payment_work where id=?", Boolean.class, payment));
                assertEquals(2, jdbc.queryForObject("select count(*) from pm_refund_history where payment_id=?", Integer.class, payment));
                assertEquals("FAILED", jdbc.queryForObject("select status from pm_refund_history where id=?", String.class, firstRefund));
                assertEquals(
                    firstSeat,
                    jdbc.queryForObject("select seat_id from oc_checkout_item where purchase_id=? and item_index=0", UUID.class, checkout)
                );
                assertEquals(
                    secondSeat,
                    jdbc.queryForObject("select seat_id from oc_order_item where purchase_id=? and item_index=1", UUID.class, checkout)
                );
                assertEquals(
                    "second@example.com",
                    jdbc.queryForObject(
                        "select attendee_email from oc_saga_line where checkout_id=? and line_index=1",
                        String.class,
                        checkout
                    )
                );
                assertEquals(
                    secondSeat,
                    jdbc.queryForObject("select reference_id from oc_saga_hold where checkout_id=? and hold_index=1", UUID.class, checkout)
                );
                assertEquals(
                    "existing-encrypted-form",
                    jdbc.queryForObject("select encrypted_payload from pm_payment_session where payment_id=?", String.class, payment)
                );
                assertEquals(543211L, jdbc.queryForObject("select nextval('pm_halyk_invoice_seq')", Long.class));
                assertEquals(
                    0,
                    jdbc.queryForObject(
                        "select count(*) from information_schema.columns where table_schema=? and column_name='snapshot'",
                        Integer.class,
                        schema
                    )
                );
            } finally {
                connection.setSchema(originalSchema);
                connection.createStatement().execute("drop schema if exists " + schema + " cascade");
            }
        }
    }

    private void migrate(DataSource source, String schema, String changelog) throws Exception {
        var migration = new SpringLiquibase();
        migration.setResourceLoader(new DefaultResourceLoader());
        migration.setDataSource(source);
        migration.setDefaultSchema(schema);
        migration.setLiquibaseSchema(schema);
        migration.setChangeLog("classpath:config/liquibase/changelog/" + changelog);
        migration.afterPropertiesSet();
    }
}
