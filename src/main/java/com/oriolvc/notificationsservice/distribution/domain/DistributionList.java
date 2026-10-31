package com.oriolvc.notificationsservice.distribution.domain;

import com.oriolvc.notificationsservice.shared.auditing.AuditedEntity;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import com.oriolvc.notificationsservice.shared.error.VersionConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;
import org.hibernate.envers.Audited;

/**
 * A customer's distribution list for one notification type and channel. Never hard-deleted: deactivation keeps it,
 * its members and the deliveries that reference them (D-5). At most one active list per (customer, type, channel)
 * (A-13), enforced by the use cases and by a partial unique index.
 */
@Entity
@Audited
@Table(name = "distribution_lists")
public class DistributionList extends AuditedEntity {

    public static final int MAX_NAME_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "distribution_lists_id_seq")
    @SequenceGenerator(
            name = "distribution_lists_id_seq",
            sequenceName = "distribution_lists_id_seq",
            allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private long customerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "notification_type_id", nullable = false, updatable = false)
    private long notificationTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false)
    private Channel channel;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DistributionList() {}

    private DistributionList(long customerId, String name, long notificationTypeId, Channel channel) {
        this.customerId = customerId;
        this.name = validName(name);
        this.notificationTypeId = notificationTypeId;
        this.channel = Objects.requireNonNull(channel, "channel");
        this.active = true;
    }

    public static DistributionList create(long customerId, String name, long notificationTypeId, Channel channel) {
        return new DistributionList(customerId, name, notificationTypeId, channel);
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

    public long version() {
        return version;
    }
}
