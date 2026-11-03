package com.biletflow.biletflow.common.security;

import com.biletflow.biletflow.common.application.AuthorizationException;
import java.util.Optional;

public interface CurrentActor {
    Optional<Long> userId();

    boolean isAdministrator();

    default Long requireUserId() {
        return userId().orElseThrow(() -> new UnauthenticatedActorException("No authenticated user is available"));
    }

    default Long requireAdministrator() {
        Long id = requireUserId();
        if (!isAdministrator()) throw new AuthorizationException("Administrator access is required");
        return id;
    }
}
