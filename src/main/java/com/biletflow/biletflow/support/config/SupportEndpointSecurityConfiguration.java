package com.biletflow.biletflow.support.config;

import com.biletflow.biletflow.common.config.security.EndpointSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SupportEndpointSecurityConfiguration {

    @Bean
    EndpointSecurityConfigurer configureSupportSecurity() {
        return authz -> authz
            .requestMatchers("/api/support/**")
            .authenticated();
    }
}
