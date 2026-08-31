package com.biletflow.biletflow.iam.config;

import com.biletflow.biletflow.iam.security.AuthoritiesConstants;
import com.biletflow.biletflow.shared.config.security.EndpointSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class IamEndpointSecurityConfiguration {

    @Bean
    EndpointSecurityConfigurer configure() {
        return authz ->
            authz
                .requestMatchers(HttpMethod.POST, "/api/authenticate")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/authenticate")
                .permitAll()
                .requestMatchers("/api/register")
                .permitAll()
                .requestMatchers("/api/activate")
                .permitAll()
                .requestMatchers("/api/account/reset-password/init")
                .permitAll()
                .requestMatchers("/api/account/reset-password/finish")
                .permitAll()
                .requestMatchers("/api/admin/**")
                .hasAuthority(AuthoritiesConstants.ADMIN);
    }
}
