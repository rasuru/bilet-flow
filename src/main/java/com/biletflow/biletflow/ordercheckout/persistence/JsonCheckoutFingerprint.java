package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.common.persistence.JsonCodec;
import com.biletflow.biletflow.ordercheckout.application.CheckoutFingerprint;
import com.biletflow.biletflow.ordercheckout.application.CheckoutService.Request;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class JsonCheckoutFingerprint implements CheckoutFingerprint {

    private final JsonCodec json;

    public JsonCheckoutFingerprint(JsonCodec json) {
        this.json = json;
    }

    @Override
    public String of(Request request) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(json.write(request).getBytes(StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
