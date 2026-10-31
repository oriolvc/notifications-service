package com.oriolvc.notificationsservice.shared.security;

import java.util.Optional;

public enum Role {
    /** Detection process (service account). Not bound to a tenant. */
    NOTIFICATION_PUBLISHER(false),
    DISTRIBUTION_LIST_ADMIN(true),
    NOTIFICATION_VIEWER(true);

    private final boolean tenantScoped;

    Role(final boolean tenantScoped) {
        this.tenantScoped = tenantScoped;
    }

    public boolean isTenantScoped() {
        return tenantScoped;
    }

    /** Spring Security authority, as checked by {@code hasRole(name())}. */
    public String authority() {
        return "ROLE_" + name();
    }

    static Optional<Role> fromClaim(final String value) {
        for (final Role role : values()) {
            if (role.name().equals(value)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
