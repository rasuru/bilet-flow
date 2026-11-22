package com.biletflow.biletflow.ordercheckout.ohs.dto;

import java.util.UUID;

/** Minimal order ownership/context projection published to Support. */
public record SupportOrderReference(UUID orderId, Long ownerUserId, UUID eventId) {}
