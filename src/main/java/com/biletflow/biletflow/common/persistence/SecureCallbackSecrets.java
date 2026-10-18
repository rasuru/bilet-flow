package com.biletflow.biletflow.common.persistence;

import com.biletflow.biletflow.common.application.CallbackSecrets;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SecureCallbackSecrets implements CallbackSecrets {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public boolean matches(String expected, String supplied) {
        return (
            expected != null &&
            supplied != null &&
            supplied.length() <= 200 &&
            MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))
        );
    }
}
