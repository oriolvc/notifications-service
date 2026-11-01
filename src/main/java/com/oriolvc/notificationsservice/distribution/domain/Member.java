package com.oriolvc.notificationsservice.distribution.domain;

import com.oriolvc.notificationsservice.shared.domain.AuditMetadata;
import java.util.Objects;

/**
 * An address on a distribution list (A-5: not a platform user). Part of the {@link DistributionList} aggregate, which
 * creates, reactivates and removes members. Removal is a soft delete (D-5); the address never changes.
 */
public class Member {

    private final Long id;
    private final String address;
    private boolean active;
    private final AuditMetadata audit;

    private Member(final Long id, final String address, final boolean active, final AuditMetadata audit) {
        this.id = id;
        this.address = Objects.requireNonNull(address, "address");
        this.active = active;
        this.audit = audit;
    }

    static Member create(final String address) {
        return new Member(null, address, true, null);
    }

    /** Rebuilds a stored member as it was saved; used by the persistence adapter. */
    public static Member restore(final long id, final String address, final boolean active, final AuditMetadata audit) {
        return new Member(id, address, active, Objects.requireNonNull(audit));
    }

    boolean hasAddress(final String other) {
        return address.equalsIgnoreCase(other);
    }

    void reactivate() {
        this.active = true;
    }

    void deactivate() {
        this.active = false;
    }

    /** {@code null} until the list is saved. */
    public Long id() {
        return id;
    }

    public String address() {
        return address;
    }

    public boolean isActive() {
        return active;
    }

    /** {@code null} until the list is saved. */
    public AuditMetadata audit() {
        return audit;
    }
}
