package com.biletflow.biletflow.promotions.config;

import com.biletflow.biletflow.common.config.security.EndpointSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class PromotionsEndpointSecurityConfiguration {

    @Bean
    EndpointSecurityConfigurer configurePromotionsSecurity() {
        return authz ->
            authz
                .requestMatchers(HttpMethod.GET, "/api/campaign-links/*")
                .permitAll()
                .requestMatchers("/api/events/*/campaigns", "/api/campaigns/**", "/api/promo-codes/**")
                .authenticated();
    }
}
