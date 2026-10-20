package com.biletflow.biletflow.common.application;

public interface CallbackSecrets {
    String generate();
    boolean matches(String expected, String supplied);
}
