package com.oriolvc.notificationsservice.shared.security;

import java.util.Optional;
import java.util.Set;

/**
 * The authenticated caller.
 *
 * @param userId the JWT {@code userId}, or the client id for service tokens. Also the auditor id.
 * @param tenantId the JWT {@code tenantId} (customer id); {@code null} for service tokens
 * @param roles the known roles in the JWT {@code roles} claim
 */
public record CurrentPrincipal(String userId, Long tenantId, Set<Role> roles) {

    public CurrentPrincipal {
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(final Role role) {
        return roles.contains(role);
    }

    /**
     * The tenant this caller's reads are limited to: none for a publisher, which works across tenants; the caller's own
     * tenant for everyone else.
     */
    public Optional<Long> readScope() {
        return hasRole(Role.NOTIFICATION_PUBLISHER) ? Optional.empty() : Optional.of(requireTenantId());
    }

    /** The tenant of a tenant-scoped caller. Tenant-scoped endpoints must only be reachable by such callers. */
    public long requireTenantId() {
        if (tenantId == null) {
            throw new IllegalStateException("Caller " + userId + " is not bound to a tenant");
        }
        return tenantId;
    }
}
