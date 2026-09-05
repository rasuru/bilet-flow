package com.biletflow.biletflow.eventmanagement.config;

import com.biletflow.biletflow.common.config.security.EndpointSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class EventManagementEndpointSecurityConfiguration {

    @Bean
    EndpointSecurityConfigurer configureEventManagementSecurity() {
        return authz ->
            authz
                .requestMatchers(HttpMethod.GET, "/api/events/mine", "/api/events/*/manage")
                .authenticated()
                .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/*", "/api/venue-layouts", "/api/venue-layouts/*")
                .permitAll()
                .requestMatchers("/api/events", "/api/events/**")
                .authenticated();
    }
}
