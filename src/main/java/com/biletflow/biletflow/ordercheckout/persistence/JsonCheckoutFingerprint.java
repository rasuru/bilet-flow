package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.common.persistence.JsonCodec;
import com.biletflow.biletflow.ordercheckout.application.CheckoutFingerprint;
import com.biletflow.biletflow.ordercheckout.application.command.CreateCheckoutCommand;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JsonCheckoutFingerprint implements CheckoutFingerprint {

    private final JsonCodec json;

    public JsonCheckoutFingerprint(JsonCodec json) {
        this.json = Objects.requireNonNull(json, "json cannot be null");
    }

    private record CheckoutWithoutPromo(UUID eventId, List<CheckoutInventoryOhs.Selection> selections) {}

    @Override
    public String of(CreateCheckoutCommand request) {
        // Preserve stored fingerprints for checkouts created before promo codes were supported.
        var payload = request.promoCode() == null ? new CheckoutWithoutPromo(request.eventId(), request.selections()) : request;
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(json.write(payload).getBytes(StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
