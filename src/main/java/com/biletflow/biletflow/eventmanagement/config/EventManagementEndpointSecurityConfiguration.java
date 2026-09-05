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
                // Public read access for browsing events
                .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/*")
                .permitAll()
                // All state mutation (creation, publishing, cancellations, ticket management) requires authentication
                .requestMatchers("/api/events", "/api/events/**")
                .authenticated();
    }
}
