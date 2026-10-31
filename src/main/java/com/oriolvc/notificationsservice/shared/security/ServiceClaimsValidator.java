package com.oriolvc.notificationsservice.shared.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Rejects tokens whose claims cannot be mapped to a {@link CurrentPrincipal}: no identity, a malformed
 * {@code tenantId}, or a tenant-scoped role without a tenant. Boot adds this bean to the decoder's validators, next to
 * the signature, expiry, issuer and audience checks, so such tokens get a 401.
 */
@Component
class ServiceClaimsValidator implements OAuth2TokenValidator<Jwt> {

    @Override
    public OAuth2TokenValidatorResult validate(final Jwt jwt) {
        final List<OAuth2Error> errors = new ArrayList<>();
        if (JwtClaims.identity(jwt) == null) {
            errors.add(invalid("The token has no userId, client_id or sub"));
        }
        Long tenantId = null;
        try {
            tenantId = JwtClaims.tenantId(jwt);
        } catch (final IllegalArgumentException e) {
            errors.add(invalid("The tenantId claim must be a number"));
        }
        final Set<Role> roles = JwtClaims.roles(jwt);
        if (tenantId == null && roles.stream().anyMatch(Role::isTenantScoped)) {
            errors.add(invalid("Tenant-scoped roles require a tenantId claim"));
        }
        return errors.isEmpty() ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(errors);
    }

    private static OAuth2Error invalid(final String description) {
        return new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null);
    }
}
