package com.biletflow.biletflow.common.config.security;

import static org.springframework.security.config.Customizer.withDefaults;

import com.biletflow.biletflow.iam.security.AuthoritiesConstants;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import tech.jhipster.config.JHipsterProperties;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    private final JHipsterProperties jHipsterProperties;
    private final Environment env;
    private final List<EndpointSecurityConfigurer> endpointSecurityConfigurers;

    public SecurityConfiguration(
        JHipsterProperties jHipsterProperties,
        Environment env,
        List<EndpointSecurityConfigurer> endpointSecurityConfigurers
    ) {
        this.jHipsterProperties = jHipsterProperties;
        this.env = env;
        this.endpointSecurityConfigurers = endpointSecurityConfigurers;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http.cors(withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz -> {
                // prettier-ignore
                endpointSecurityConfigurers.forEach(configurer -> configurer.configure(authz));

                authz
                    .requestMatchers("/api/**")
                    .authenticated()
                    .requestMatchers("/websocket/**")
                    .authenticated()
                    .requestMatchers("/management/health")
                    .permitAll()
                    .requestMatchers("/management/health/**")
                    .permitAll()
                    .requestMatchers("/management/info")
                    .permitAll()
                    .requestMatchers("/management/prometheus")
                    .permitAll()
                    .requestMatchers("/management/**")
                    .hasAuthority(AuthoritiesConstants.ADMIN);

                if (env.acceptsProfiles(Profiles.of("dev"))) {
                    authz
                        .requestMatchers("/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/swagger-ui.html")
                        .permitAll();
                } else {
                    authz.requestMatchers("/v3/api-docs/**").hasAuthority(AuthoritiesConstants.ADMIN);
                }
            })
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
        return http.build();
    }
}
