package com.biletflow.biletflow.paymentsmonetization.config;

import com.biletflow.biletflow.common.config.security.EndpointSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class PaymentsEndpointSecurityConfiguration {

    @Bean
    public EndpointSecurityConfigurer paymentsEndpointSecurityConfigurer() {
        return authz -> authz.requestMatchers(HttpMethod.POST, "/api/payments/halyk/callback").permitAll();
    }
}
