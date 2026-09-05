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
                // Organizer reads must come before /api/events/*
                .requestMatchers(HttpMethod.GET, "/api/events/mine", "/api/events/*/manage")
                .authenticated()

                // Public Event Management reads
                .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/*", "/api/venue-layouts", "/api/venue-layouts/*")
                .permitAll()

                // Event Management mutations only
                .requestMatchers(HttpMethod.POST, "/api/events")
                .authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/events/*")
                .authenticated()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/events/*/publish",
                    "/api/events/*/unpublish",
                    "/api/events/*/cancel",
                    "/api/events/*/duplicate",
                    "/api/events/*/venue",
                    "/api/events/*/staff"
                )
                .authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/events/*/staff/*")
                .authenticated();
    }
}
