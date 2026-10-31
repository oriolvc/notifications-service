package com.oriolvc.notificationsservice.distribution.domain;

import com.oriolvc.notificationsservice.shared.domain.AuditMetadata;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import com.oriolvc.notificationsservice.shared.error.VersionConflictException;
import java.util.Objects;

/**
 * A customer's distribution list for one notification type and channel. Never hard-deleted: deactivation keeps it,
 * its members and the deliveries that reference them (D-5). At most one active list per (customer, type, channel)
 * (A-13), enforced by a partial unique index.
 */
public class DistributionList {

    public static final int MAX_NAME_LENGTH = 200;

    private final Long id;
    private final long customerId;
    private String name;
    private final long notificationTypeId;
    private final Channel channel;
    private boolean active;
    private final long version;
    private final AuditMetadata audit;

    private DistributionList(
            Long id,
            long customerId,
            String name,
            long notificationTypeId,
            Channel channel,
            boolean active,
            long version,
            AuditMetadata audit) {
        this.id = id;
        this.customerId = customerId;
        this.name = name;
        this.notificationTypeId = notificationTypeId;
        this.channel = Objects.requireNonNull(channel, "channel");
        this.active = active;
        this.version = version;
        this.audit = audit;
    }

    public static DistributionList create(long customerId, String name, long notificationTypeId, Channel channel) {
        return new DistributionList(null, customerId, validName(name), notificationTypeId, channel, true, 0, null);
    }

    /** Rebuilds a stored list as it was saved; used by the persistence adapter. */
    public static DistributionList restore(
            long id,
            long customerId,
            String name,
            long notificationTypeId,
            Channel channel,
            boolean active,
            long version,
            AuditMetadata audit) {
        return new DistributionList(
                id, customerId, name, notificationTypeId, channel, active, version, Objects.requireNonNull(audit));
    }

    /** Optimistic locking for client edits: the client must have read the current version. */
    public void checkVersion(long expectedVersion) {
        if (version != expectedVersion) {
            throw new VersionConflictException("Distribution list " + id + " is at version " + version + ", not "
                    + expectedVersion + "; read it again and retry");
        }
    }

    public void rename(String newName) {
        this.name = validName(newName);
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    /** Leading and trailing spaces are not part of the name. */
    public static String normalizeName(String name) {
        return name == null ? null : name.strip();
    }

    private static String validName(String name) {
        String normalized = normalizeName(name);
        if (normalized == null || normalized.isEmpty() || normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("A list name must have 1 to " + MAX_NAME_LENGTH + " characters");
        }
        return normalized;
    }

    /** {@code null} until the list is saved. */
    public Long id() {
        return id;
    }

    public long customerId() {
        return customerId;
    }

    public String name() {
        return name;
    }

    public long notificationTypeId() {
        return notificationTypeId;
    }

    public Channel channel() {
        return channel;
    }

    public boolean isActive() {
        return active;
    }

    /** The version this list was read at; the saved copy carries the next one. */
    public long version() {
        return version;
    }

    /** {@code null} until the list is saved. */
    public AuditMetadata audit() {
        return audit;
    }
}
