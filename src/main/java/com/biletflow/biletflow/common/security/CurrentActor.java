package com.biletflow.biletflow.common.security;

import java.util.Optional;

public interface CurrentActor {
    Optional<Long> userId();

    default Long requireUserId() {
        return userId().orElseThrow(() -> new UnauthenticatedActorException("No authenticated user is available"));
    }
}
