package com.oriolvc.notificationsservice.shared.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Placeholder chain: API documentation and health are public, springdoc's generated spec is denied (the static spec is
 * the contract), everything else needs authentication. T3 replaces the authentication with the OAuth2 resource server
 * and adds the per-endpoint role rules.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/openapi/**",
        "/schemas/**",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/swagger-config",
        "/actuator/health"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml")
                        .denyAll()
                        .anyRequest()
                        .authenticated())
                .httpBasic(withDefaults());
        return http.build();
    }
}
