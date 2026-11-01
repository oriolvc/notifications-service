package com.oriolvc.notificationsservice.distribution.domain;

import com.oriolvc.notificationsservice.shared.domain.AuditMetadata;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import com.oriolvc.notificationsservice.shared.error.VersionConflictException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A customer's distribution list for one notification type and channel. Never hard-deleted: deactivation keeps it,
 * its members and the deliveries that reference them (D-5). At most one active list per (customer, type, channel)
 * (A-13), enforced by a partial unique index. Members are added and removed only through the list, which keeps them
 * unique by address (ignoring case) and at most {@value #MAX_ACTIVE_MEMBERS} active.
 */
public class DistributionList {

    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_ACTIVE_MEMBERS = 100;

    private final Long id;
    private final long customerId;
    private String name;
    private final long notificationTypeId;
    private final Channel channel;
    private boolean active;
    private final long version;
    private final AuditMetadata audit;
    private final List<Member> members;

    private DistributionList(
            final Long id,
            final long customerId,
            final String name,
            final long notificationTypeId,
            final Channel channel,
            final boolean active,
            final long version,
            final AuditMetadata audit,
            final List<Member> members) {
        this.id = id;
        this.customerId = customerId;
        this.name = name;
        this.notificationTypeId = notificationTypeId;
        this.channel = Objects.requireNonNull(channel, "channel");
        this.active = active;
        this.version = version;
        this.audit = audit;
        this.members = new ArrayList<>(members);
    }

    public static DistributionList create(
            final long customerId, final String name, final long notificationTypeId, final Channel channel) {
        return new DistributionList(
                null, customerId, validName(name), notificationTypeId, channel, true, 0, null, List.of());
    }

    /** Rebuilds a stored list as it was saved; used by the persistence adapter. */
    public static DistributionList restore(
            final long id,
            final long customerId,
            final String name,
            final long notificationTypeId,
            final Channel channel,
            final boolean active,
            final long version,
            final AuditMetadata audit,
            final List<Member> members) {
        return new DistributionList(
                id,
                customerId,
                name,
                notificationTypeId,
                channel,
                active,
                version,
                Objects.requireNonNull(audit),
                members);
    }

    /** Optimistic locking for client edits: the client must have read the current version. */
    public void checkVersion(final long expectedVersion) {
        if (version != expectedVersion) {
            throw new VersionConflictException("Distribution list " + id + " is at version " + version + ", not "
                    + expectedVersion + "; read it again and retry");
        }
    }

    public void rename(final String newName) {
        this.name = validName(newName);
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    /**
     * Adds the address, or reactivates it if it was removed. The address is trimmed and checked by the validator of
     * the list's channel.
     *
     * @return the new or reactivated member (without an id until the list is saved)
     * @throws InvalidAddressException if the address is not valid for the channel (400)
     * @throws MemberAlreadyActiveException if the address is already an active member (409)
     * @throws MemberLimitReachedException if the list already has {@value #MAX_ACTIVE_MEMBERS} active members (422)
     */
    public Member addMember(final String address, final AddressValidator validator) {
        if (validator.channel() != channel) {
            throw new IllegalArgumentException("A " + validator.channel() + " validator cannot check " + channel);
        }
        final String trimmed = address == null ? "" : address.strip();
        validator.validate(trimmed);
        final Optional<Member> existing = memberWithAddress(trimmed);
        if (existing.isPresent() && existing.get().isActive()) {
            throw new MemberAlreadyActiveException(id, existing.get().id());
        }
        if (activeMembers().size() >= MAX_ACTIVE_MEMBERS) {
            throw new MemberLimitReachedException(id);
        }
        if (existing.isPresent()) {
            existing.get().reactivate();
            return existing.get();
        }
        final Member member = Member.create(trimmed);
        members.add(member);
        return member;
    }

    /** Soft delete; removing an inactive member changes nothing. */
    public void removeMember(final long memberId) {
        member(memberId).deactivate();
    }

    public Member member(final long memberId) {
        return members.stream()
                .filter(member -> member.id() != null && member.id() == memberId)
                .findFirst()
                .orElseThrow(() -> new MemberNotFoundException(id, memberId));
    }

    public Optional<Member> memberWithAddress(final String address) {
        return members.stream().filter(member -> member.hasAddress(address)).findFirst();
    }

    /** All members, removed ones included, in the order they were added. */
    public List<Member> members() {
        return List.copyOf(members);
    }

    public List<Member> activeMembers() {
        return members.stream().filter(Member::isActive).toList();
    }

    /** Leading and trailing spaces are not part of the name. */
    public static String normalizeName(final String name) {
        return name == null ? null : name.strip();
    }

    private static String validName(final String name) {
        final String normalized = normalizeName(name);
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
