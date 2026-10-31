package com.oriolvc.notificationsservice.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentPrincipalTest {

    private final CurrentPrincipalProvider provider = new CurrentPrincipalProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerUserIsMappedFromTheJwtClaims() {
        authenticate(jwt(Map.of("userId", "alice", "tenantId", 101, "roles", List.of("DISTRIBUTION_LIST_ADMIN", "X"))));

        final CurrentPrincipal principal = provider.current();

        assertThat(principal).isEqualTo(new CurrentPrincipal("alice", 101L, Set.of(Role.DISTRIBUTION_LIST_ADMIN)));
        assertThat(principal.requireTenantId()).isEqualTo(101);
        assertThat(principal.hasRole(Role.DISTRIBUTION_LIST_ADMIN)).isTrue();
        assertThat(principal.hasRole(Role.NOTIFICATION_PUBLISHER)).isFalse();
    }

    @Test
    void serviceTokenUsesClientIdAndHasNoTenant() {
        authenticate(jwt(Map.of("client_id", "detection-process", "roles", List.of("NOTIFICATION_PUBLISHER"))));

        final CurrentPrincipal principal = provider.current();

        assertThat(principal.userId()).isEqualTo("detection-process");
        assertThat(principal.tenantId()).isNull();
        assertThatThrownBy(principal::requireTenantId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stringTenantIdIsAccepted() {
        assertThat(JwtClaims.tenantId(jwt(Map.of("tenantId", "102")))).isEqualTo(102L);
        assertThat(JwtClaims.roles(jwt(Map.of("roles", "NOT_A_LIST")))).isEmpty();
    }

    @Test
    void noPrincipalOutsideAnAuthenticatedRequest() {
        assertThat(CurrentPrincipalProvider.find()).isEmpty();
        assertThatThrownBy(provider::current).isInstanceOf(IllegalStateException.class);

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "pw"));
        assertThat(CurrentPrincipalProvider.find()).isEmpty();
    }

    @Test
    void validatorRejectsTokensThatCannotBeMapped() {
        final var validator = new ServiceClaimsValidator();

        assertThat(validator.validate(jwt(Map.of("tenantId", List.of(1)))).getErrors())
                .extracting(error -> error.getDescription())
                .containsExactlyInAnyOrder(
                        "The token has no userId, client_id or sub", "The tenantId claim must be a number");
        assertThat(validator
                        .validate(jwt(Map.of("userId", "u", "roles", List.of("NOTIFICATION_VIEWER"))))
                        .getErrors())
                .extracting(error -> error.getDescription())
                .containsExactly("Tenant-scoped roles require a tenantId claim");
        assertThat(validator
                        .validate(jwt(Map.of("client_id", "c", "roles", List.of("NOTIFICATION_PUBLISHER"))))
                        .hasErrors())
                .isFalse();
    }

    private static Jwt jwt(final Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"), claims);
    }

    private static void authenticate(final Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new PrincipalJwtAuthenticationConverter().convert(jwt));
    }
}
