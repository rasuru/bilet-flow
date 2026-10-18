package com.biletflow.biletflow.paymentsmonetization.infrastructure.halyk;

import java.net.http.HttpRequest;

/** Transport seam allows deterministic adapter contract tests without contacting the bank. */
public interface HalykHttp {
    String send(HttpRequest request);
}
