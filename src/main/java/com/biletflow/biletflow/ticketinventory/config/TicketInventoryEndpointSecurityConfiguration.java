package com.biletflow.biletflow.ticketinventory.config;

import com.biletflow.biletflow.common.config.security.EndpointSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class TicketInventoryEndpointSecurityConfiguration {

    @Bean
    EndpointSecurityConfigurer configureTicketInventorySecurity() {
        return authz ->
            authz
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/events/*/ticket-types",
                    "/api/events/*/inventory",
                    "/api/events/*/availability",
                    "/api/events/*/seat-map"
                )
                .permitAll()
                .requestMatchers(
                    "/api/events/*/ticket-types/manage",
                    "/api/events/*/ticket-types/general-admission",
                    "/api/events/*/ticket-types/assigned-seating",
                    "/api/ticket-types/**",
                    "/api/tickets/**"
                )
                .authenticated();
    }
}
