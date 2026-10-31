package com.oriolvc.notificationsservice.shared.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Gives the web adapters the authenticated caller, so they can pass the tenant from the JWT into commands. */
@Component
public class CurrentPrincipalProvider {

    /** The caller of the current request. Only call on authenticated endpoints. */
    public CurrentPrincipal current() {
        return find().orElseThrow(() -> new IllegalStateException("No authenticated caller"));
    }

    /** The caller, if the current thread is serving an authenticated request. */
    public static Optional<CurrentPrincipal> find() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return Optional.of(JwtClaims.toPrincipal(token.getToken()));
        }
        return Optional.empty();
    }
}
