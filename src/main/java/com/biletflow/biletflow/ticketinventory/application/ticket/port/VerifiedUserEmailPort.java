package com.biletflow.biletflow.ticketinventory.application.ticket.port;

import java.util.Optional;

/**
 * Synchronous application-layer port to Identity & Access.
 *
 * The adapter should call Identity's OHS, not Identity's repositories/domain objects.
 */
public interface VerifiedUserEmailPort {
    Optional<String> findVerifiedEmail(Long userId);
}
