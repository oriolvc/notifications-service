package com.oriolvc.notificationsservice.notification.domain;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.time.Instant;
import java.util.Objects;

/**
 * One message to one recipient. The address and the rendered content are snapshots taken when the notification was
 * accepted, so later list changes do not affect it (AC-15, AC-16) and the record shows exactly what was sent (D-7).
 */
public final class Delivery {

    private final Long id;
    private final long distributionListId;
    private final long memberId;
    private final Channel channel;
    private final String address;
    private final long templateId;
    private final RenderedMessage content;
    private final DeliveryStatus status;
    private final int attemptCount;
    private final Instant nextAttemptAt;

    private Delivery(
            final Long id,
            final long distributionListId,
            final long memberId,
            final Channel channel,
            final String address,
            final long templateId,
            final RenderedMessage content,
            final DeliveryStatus status,
            final int attemptCount,
            final Instant nextAttemptAt) {
        this.id = id;
        this.distributionListId = distributionListId;
        this.memberId = memberId;
        this.channel = Objects.requireNonNull(channel, "channel");
        this.address = Objects.requireNonNull(address, "address");
        this.templateId = templateId;
        this.content = Objects.requireNonNull(content, "content");
        this.status = Objects.requireNonNull(status, "status");
        this.attemptCount = attemptCount;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
    }

    /** A new delivery, due immediately. */
    static Delivery pending(
            final Recipient recipient, final Template template, final RenderedMessage content, final Instant now) {
        return new Delivery(
                null,
                recipient.distributionListId(),
                recipient.memberId(),
                recipient.channel(),
                recipient.address(),
                template.id(),
                content,
                DeliveryStatus.PENDING,
                0,
                now);
    }

    /** Rebuilds a stored delivery. */
    public static Delivery restore(
            final long id,
            final long distributionListId,
            final long memberId,
            final Channel channel,
            final String address,
            final long templateId,
            final RenderedMessage content,
            final DeliveryStatus status,
            final int attemptCount,
            final Instant nextAttemptAt) {
        return new Delivery(
                id,
                distributionListId,
                memberId,
                channel,
                address,
                templateId,
                content,
                status,
                attemptCount,
                nextAttemptAt);
    }

    public Long id() {
        return id;
    }

    public long distributionListId() {
        return distributionListId;
    }

    public long memberId() {
        return memberId;
    }

    public Channel channel() {
        return channel;
    }

    public String address() {
        return address;
    }

    public long templateId() {
        return templateId;
    }

    public RenderedMessage content() {
        return content;
    }

    public DeliveryStatus status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }
}
