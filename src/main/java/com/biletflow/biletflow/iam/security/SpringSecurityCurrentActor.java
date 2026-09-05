package com.biletflow.biletflow.iam.security;

import com.biletflow.biletflow.common.security.CurrentActor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityCurrentActor implements CurrentActor {

    @Override
    public Optional<Long> userId() {
        return SecurityUtils.getCurrentUserId();
    }
}
