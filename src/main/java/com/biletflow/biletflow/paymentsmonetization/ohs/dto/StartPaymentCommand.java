package com.biletflow.biletflow.paymentsmonetization.ohs.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StartPaymentCommand(UUID orderId, UUID eventId, long payerId, BigDecimal amount, Instant expiresAt) {}
