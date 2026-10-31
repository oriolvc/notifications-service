package com.oriolvc.notificationsservice.shared.security;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/** Reads the service's claims ({@code tenantId}, {@code userId}, {@code roles}) from a validated JWT. */
final class JwtClaims {

    static final String TENANT_ID = "tenantId";
    static final String USER_ID = "userId";
    static final String ROLES = "roles";
    static final String CLIENT_ID = "client_id";

    private JwtClaims() {}

    static CurrentPrincipal toPrincipal(Jwt jwt) {
        return new CurrentPrincipal(identity(jwt), tenantId(jwt), roles(jwt));
    }

    /** {@code userId}, falling back to the client id and then {@code sub} for service tokens. */
    static String identity(Jwt jwt) {
        return Stream.of(jwt.getClaimAsString(USER_ID), jwt.getClaimAsString(CLIENT_ID), jwt.getSubject())
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    /** The {@code tenantId} claim as a number; {@code null} if absent. */
    static Long tenantId(Jwt jwt) {
        Object value = jwt.getClaim(TENANT_ID);
        return switch (value) {
            case null -> null;
            case Number number -> number.longValue();
            case String text -> Long.valueOf(text);
            default -> throw new IllegalArgumentException("tenantId must be a number");
        };
    }

    static Set<Role> roles(Jwt jwt) {
        Object value = jwt.getClaim(ROLES);
        Collection<?> claimed = value instanceof Collection<?> values ? values : List.of();
        Set<Role> roles = EnumSet.noneOf(Role.class);
        claimed.forEach(role -> Role.fromClaim(String.valueOf(role)).ifPresent(roles::add));
        return roles;
    }
}
