package com.oriolvc.notificationsservice.shared.security;

import static com.oriolvc.notificationsservice.shared.security.Role.DISTRIBUTION_LIST_ADMIN;
import static com.oriolvc.notificationsservice.shared.security.Role.NOTIFICATION_PUBLISHER;
import static com.oriolvc.notificationsservice.shared.security.Role.NOTIFICATION_VIEWER;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;

import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The only place where endpoint authorization is defined. It mirrors {@code x-required-roles} in the OpenAPI spec
 * (checked by {@code SecurityRulesMirrorSpecIntegrationTest}). Tenant scoping happens in the application layer.
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
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ProblemDetailsAuthenticationEntryPoint authenticationEntryPoint,
            ProblemDetailsAccessDeniedHandler accessDeniedHandler) {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        // springdoc's generated spec: the static spec is the contract (RFC 6.3)
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml")
                        .denyAll()
                        .requestMatchers(POST, "/notifications")
                        .hasRole(NOTIFICATION_PUBLISHER.name())
                        .requestMatchers(GET, "/notifications", "/notifications/*")
                        .hasAnyRole(names(NOTIFICATION_PUBLISHER, DISTRIBUTION_LIST_ADMIN, NOTIFICATION_VIEWER))
                        .requestMatchers(GET, "/distribution-lists", "/distribution-lists/**")
                        .hasAnyRole(names(DISTRIBUTION_LIST_ADMIN, NOTIFICATION_VIEWER))
                        .requestMatchers(POST, "/distribution-lists", "/distribution-lists/**")
                        .hasRole(DISTRIBUTION_LIST_ADMIN.name())
                        .requestMatchers(PATCH, "/distribution-lists/**")
                        .hasRole(DISTRIBUTION_LIST_ADMIN.name())
                        .requestMatchers(DELETE, "/distribution-lists/**")
                        .hasRole(DISTRIBUTION_LIST_ADMIN.name())
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new PrincipalJwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    private static String[] names(Role... roles) {
        return Arrays.stream(roles).map(Role::name).toArray(String[]::new);
    }
}
